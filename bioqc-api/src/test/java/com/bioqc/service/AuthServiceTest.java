package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bioqc.service.AuditService;
import com.bioqc.dto.request.LoginRequest;
import com.bioqc.dto.request.RegisterRequest;
import com.bioqc.entity.RefreshTokenSession;
import com.bioqc.entity.Role;
import com.bioqc.entity.User;
import com.bioqc.exception.BusinessException;
import com.bioqc.repository.RefreshTokenSessionRepository;
import com.bioqc.repository.UserRepository;
import com.bioqc.security.AccessTokenBlacklistService;
import com.bioqc.security.JwtTokenProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenSessionRepository refreshTokenSessionRepository;

    private JwtTokenProvider jwtTokenProvider;
    private AccessTokenBlacklistService accessTokenBlacklistService;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
            "test-secret-key-that-is-at-least-256-bits-long-for-testing",
            "test-issuer",
            900_000,
            604_800_000
        );
        accessTokenBlacklistService = new AccessTokenBlacklistService();
        authService = new AuthService(
            userRepository,
            passwordEncoder,
            jwtTokenProvider,
            refreshTokenSessionRepository,
            accessTokenBlacklistService,
            new AuditService(null, null, new com.fasterxml.jackson.databind.ObjectMapper())
        );
    }

    @Test
    @DisplayName("deve fazer login com credenciais válidas")
    void shouldLoginWithValidCredentials() {
        User user = activeUser();
        when(userRepository.findByUsername("ana")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("eva123", user.getPasswordHash())).thenReturn(true);

        var response = authService.login(new LoginRequest("ana", "eva123"));

        assertThat(response.response().accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("deve lançar erro para senha inválida")
    void shouldThrowOnInvalidPassword() {
        User user = activeUser();
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana", "wrong")))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("deve lançar erro para username inexistente")
    void shouldThrowOnNonExistentUsername() {
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown", "eva123")))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("deve lançar erro para usuário inativo")
    void shouldThrowOnInactiveUser() {
        User user = activeUser();
        user.setIsActive(false);
        when(userRepository.findByUsername(any())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana", "eva123")))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("deve renovar token com sucesso")
    void shouldRefreshTokenSuccessfully() {
        User user = activeUser();
        UUID tokenId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String refreshToken = jwtTokenProvider.generateRefreshToken(user, tokenId, familyId);
        when(refreshTokenSessionRepository.findByTokenId(tokenId)).thenReturn(Optional.of(
            RefreshTokenSession.builder()
                .user(user)
                .tokenId(tokenId)
                .familyId(familyId)
                .tokenHash(sha256(refreshToken))
                .expiresAt(Instant.now().plusSeconds(600))
                .build()
        ));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        var response = authService.refreshToken(refreshToken);

        assertThat(response.response().accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("deve lançar erro para refresh token expirado")
    void shouldThrowOnExpiredRefreshToken() {
        assertThatThrownBy(() -> authService.refreshToken("expired-token"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("deve rejeitar access token usado no refresh")
    void shouldRejectAccessTokenOnRefresh() {
        String accessToken = jwtTokenProvider.generateAccessToken(activeUser());

        assertThatThrownBy(() -> authService.refreshToken(accessToken))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("deve registrar novo usuário")
    void shouldRegisterNewUser() {
        when(userRepository.existsByUsername("novo")).thenReturn(false);
        when(passwordEncoder.encode("teste123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(new RegisterRequest("novo", "teste123", "Novo Usuario", "ADMIN", null));

        assertThat(response.username()).isEqualTo("novo");
    }

    @Test
    @DisplayName("deve lançar erro para username duplicado")
    void shouldThrowOnDuplicateUsername() {
        when(userRepository.existsByUsername("novo")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("novo", "teste123", "Novo", "ADMIN", null)))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("deve incluir access token em blacklist no logout")
    void shouldBlacklistAccessTokenOnLogout() {
        String accessToken = jwtTokenProvider.generateAccessToken(activeUser());
        JwtTokenProvider.TokenDetails details = jwtTokenProvider.validateAccessToken(accessToken);

        authService.logout(accessToken, null);

        assertThat(accessTokenBlacklistService.isBlacklisted(details.tokenId())).isTrue();
    }

    private User activeUser() {
        return User.builder()
            .id(UUID.randomUUID())
            .username("ana")
            .email("ana@bio.com")
            .passwordHash("hash")
            .name("Ana")
            .role(Role.ADMIN)
            .permissions(Set.of())
            .isActive(true)
            .build();
    }

    private String sha256(String token) {
        try {
            return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
