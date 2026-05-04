package com.bioqc.security;

import com.bioqc.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    public enum TokenType {
        ACCESS,
        REFRESH
    }

    public record TokenDetails(
        UUID userId,
        UUID tokenId,
        UUID familyId,
        String username,
        String role,
        TokenType tokenType,
        Instant expiration
    ) {
    }

    private final SecretKey secretKey;
    private final String issuer;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;
    private final JwtParser jwtParser;

    public JwtTokenProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.issuer:bioqc-api}") String issuer,
        @Value("${jwt.access-token-expiry}") long accessTokenExpiry,
        @Value("${jwt.refresh-token-expiry}") long refreshTokenExpiry
    ) {
        this.secretKey = buildKey(secret);
        this.issuer = issuer;
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
        this.jwtParser = Jwts.parser()
            .verifyWith(secretKey)
            .requireIssuer(issuer)
            .build();
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        UUID tokenId = UUID.randomUUID();
        return Jwts.builder()
            .subject(user.getId().toString())
            .issuer(issuer)
            .claim("username", user.getUsername())
            .claim("role", user.getRole().name())
            .claim("token_type", TokenType.ACCESS.name())
            .id(tokenId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(accessTokenExpiry)))
            .signWith(secretKey)
            .compact();
    }

    public String generateRefreshToken(User user, UUID tokenId, UUID familyId) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getId().toString())
            .issuer(issuer)
            .claim("token_type", TokenType.REFRESH.name())
            .claim("family_id", familyId.toString())
            .id(tokenId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(refreshTokenExpiry)))
            .signWith(secretKey)
            .compact();
    }

    public TokenDetails validateAccessToken(String token) {
        TokenDetails details = parseToken(token);
        if (details.tokenType() != TokenType.ACCESS) {
            throw new JwtException("Token não é do tipo ACCESS");
        }
        if (details.username() == null || details.role() == null) {
            throw new JwtException("Claims obrigatórias ausentes no access token");
        }
        return details;
    }

    public TokenDetails validateRefreshToken(String token) {
        TokenDetails details = parseToken(token);
        if (details.tokenType() != TokenType.REFRESH) {
            throw new JwtException("Token não é do tipo REFRESH");
        }
        if (details.familyId() == null) {
            throw new JwtException("Family id ausente no refresh token");
        }
        return details;
    }

    public boolean isAccessTokenValid(String token) {
        try {
            validateAccessToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token) {
        try {
            validateRefreshToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public long getRefreshTokenMaxAgeSeconds() {
        return refreshTokenExpiry / 1000;
    }

    private TokenDetails parseToken(String token) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get("token_type", String.class);
        String familyId = claims.get("family_id", String.class);
        return new TokenDetails(
            UUID.fromString(claims.getSubject()),
            UUID.fromString(claims.getId()),
            familyId == null ? null : UUID.fromString(familyId),
            claims.get("username", String.class),
            claims.get("role", String.class),
            TokenType.valueOf(tokenType),
            claims.getExpiration().toInstant()
        );
    }

    private Claims parseClaims(String token) {
        return jwtParser.parseSignedClaims(token).getPayload();
    }

    private SecretKey buildKey(String secret) {
        String normalizedSecret = secret == null ? "" : secret.trim();
        byte[] keyBytes = normalizedSecret.getBytes(StandardCharsets.UTF_8);

        if (looksEncoded(normalizedSecret)) {
            try {
                keyBytes = Decoders.BASE64.decode(normalizedSecret);
            } catch (RuntimeException ignored) {
                try {
                    keyBytes = Decoders.BASE64URL.decode(normalizedSecret);
                } catch (RuntimeException ignoredAgain) {
                    keyBytes = normalizedSecret.getBytes(StandardCharsets.UTF_8);
                }
            }
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private boolean looksEncoded(String secret) {
        return secret.length() >= 44 && secret.matches("^[A-Za-z0-9+/=_-]+$");
    }
}
