package com.bioqc.service;

import com.bioqc.dto.request.LoginRequest;
import com.bioqc.dto.request.RegisterRequest;
import com.bioqc.dto.response.AuthResponse;
import com.bioqc.dto.response.UserResponse;
import com.bioqc.entity.Permission;
import com.bioqc.entity.RefreshTokenSession;
import com.bioqc.entity.Role;
import com.bioqc.entity.User;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.RefreshTokenSessionRepository;
import com.bioqc.repository.UserRepository;
import com.bioqc.security.AccessTokenBlacklistService;
import com.bioqc.security.JwtTokenProvider;
import com.bioqc.util.ResponseMapper;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    public record IssuedAuthSession(AuthResponse response, String refreshToken) {
    }

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final AccessTokenBlacklistService accessTokenBlacklistService;
    private final AuditService auditService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider jwtTokenProvider,
        RefreshTokenSessionRepository refreshTokenSessionRepository,
        AccessTokenBlacklistService accessTokenBlacklistService,
        AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.accessTokenBlacklistService = accessTokenBlacklistService;
        this.auditService = auditService;
    }

    @Transactional
    public IssuedAuthSession login(LoginRequest request) {
        String normalizedUsername = normalizeUsername(request.username());
        User user = userRepository.findByUsername(normalizedUsername)
            .orElseThrow(() -> new BusinessException("Credenciais inválidas"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException("Usuário inativo");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("Credenciais inválidas");
        }

        auditService.log("LOGIN", "User", user.getId(), Map.of("username", user.getUsername()));
        return issueSession(user, null, null);
    }

    @Transactional
    public IssuedAuthSession refreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException("Refresh token inválido ou expirado");
        }

        JwtTokenProvider.TokenDetails tokenDetails;
        try {
            tokenDetails = jwtTokenProvider.validateRefreshToken(refreshToken);
        } catch (RuntimeException exception) {
            throw new BusinessException("Refresh token inválido ou expirado");
        }

        RefreshTokenSession refreshTokenSession = refreshTokenSessionRepository.findByTokenId(tokenDetails.tokenId())
            .orElseThrow(() -> new BusinessException("Refresh token inválido ou expirado"));

        Instant now = Instant.now();
        if (refreshTokenSession.getRevokedAt() != null
            || refreshTokenSession.getExpiresAt().isBefore(now)
            || !refreshTokenSession.getFamilyId().equals(tokenDetails.familyId())
            || !refreshTokenSession.getTokenHash().equals(hashToken(refreshToken))) {
            throw new BusinessException("Refresh token inválido ou expirado");
        }

        User user = userRepository.findById(tokenDetails.userId())
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException("Usuário inativo");
        }

        refreshTokenSession.setLastUsedAt(now);
        refreshTokenSession.setRevokedAt(now);

        return issueSession(user, refreshTokenSession.getFamilyId(), refreshTokenSession.getTokenId());
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (StringUtils.hasText(accessToken) && jwtTokenProvider.isAccessTokenValid(accessToken)) {
            JwtTokenProvider.TokenDetails accessTokenDetails = jwtTokenProvider.validateAccessToken(accessToken);
            accessTokenBlacklistService.blacklist(accessTokenDetails.tokenId(), accessTokenDetails.expiration());
        }

        if (StringUtils.hasText(refreshToken) && jwtTokenProvider.isRefreshTokenValid(refreshToken)) {
            JwtTokenProvider.TokenDetails refreshTokenDetails = jwtTokenProvider.validateRefreshToken(refreshToken);
            refreshTokenSessionRepository.findByTokenId(refreshTokenDetails.tokenId())
                .ifPresent(session -> {
                    if (session.getTokenHash().equals(hashToken(refreshToken)) && session.getRevokedAt() == null) {
                        session.setRevokedAt(Instant.now());
                    }
                });
        }
    }

    public long getRefreshTokenCookieMaxAgeSeconds() {
        return jwtTokenProvider.getRefreshTokenMaxAgeSeconds();
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedUsername = normalizeUsername(request.username());
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new BusinessException("Já existe um usuário com este nome de usuário");
        }

        Role role = parseRole(request.role());

        User user = User.builder()
            .username(normalizedUsername)
            .email(request.email() != null ? request.email().trim() : null)
            .passwordHash(passwordEncoder.encode(request.password()))
            .name(request.name().trim())
            .role(role)
            .permissions(Set.of())
            .isActive(Boolean.TRUE)
            .build();

        return ResponseMapper.toUserResponse(userRepository.save(user));
    }

    private IssuedAuthSession issueSession(User user, UUID familyId, UUID rotatedFromTokenId) {
        UUID tokenId = UUID.randomUUID();
        UUID effectiveFamilyId = familyId == null ? UUID.randomUUID() : familyId;
        String refreshToken = jwtTokenProvider.generateRefreshToken(user, tokenId, effectiveFamilyId);
        JwtTokenProvider.TokenDetails refreshTokenDetails = jwtTokenProvider.validateRefreshToken(refreshToken);

        refreshTokenSessionRepository.save(RefreshTokenSession.builder()
            .user(user)
            .tokenId(tokenId)
            .familyId(effectiveFamilyId)
            .rotatedFromTokenId(rotatedFromTokenId)
            .tokenHash(hashToken(refreshToken))
            .expiresAt(refreshTokenDetails.expiration())
            .build());

        return new IssuedAuthSession(
            new AuthResponse(
                jwtTokenProvider.generateAccessToken(user),
                null,
                ResponseMapper.toUserResponse(user)
            ),
            refreshToken
        );
    }

    static Role parseRole(String role) {
        String normalized = role == null ? "FUNCIONARIO" : role.trim().toUpperCase();
        try {
            return Role.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("Role inválida: " + normalized);
        }
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível para hash de token", exception);
        }
    }
}
