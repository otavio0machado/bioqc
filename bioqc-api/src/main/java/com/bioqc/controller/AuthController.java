package com.bioqc.controller;

import com.bioqc.dto.request.ForgotPasswordRequest;
import com.bioqc.dto.request.LoginRequest;
import com.bioqc.dto.request.RefreshTokenRequest;
import com.bioqc.dto.request.RegisterRequest;
import com.bioqc.dto.request.ResetPasswordRequest;
import com.bioqc.dto.response.AuthResponse;
import com.bioqc.dto.response.PasswordResetResponse;
import com.bioqc.dto.response.UserResponse;
import com.bioqc.security.TokenCookieService;
import com.bioqc.service.AuthService;
import com.bioqc.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final TokenCookieService tokenCookieService;

    public AuthController(
        AuthService authService,
        PasswordResetService passwordResetService,
        TokenCookieService tokenCookieService
    ) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.tokenCookieService = tokenCookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletResponse response
    ) {
        AuthService.IssuedAuthSession authSession = authService.login(request);
        response.addHeader(
            "Set-Cookie",
            tokenCookieService.createRefreshCookieValue(
                authSession.refreshToken(),
                authService.getRefreshTokenCookieMaxAgeSeconds()
            )
        );
        return ResponseEntity.ok(authSession.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
        @RequestBody(required = false) RefreshTokenRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse response
    ) {
        String refreshToken = resolveRefreshToken(httpRequest, request);
        AuthService.IssuedAuthSession authSession = authService.refreshToken(refreshToken);
        response.addHeader(
            "Set-Cookie",
            tokenCookieService.createRefreshCookieValue(
                authSession.refreshToken(),
                authService.getRefreshTokenCookieMaxAgeSeconds()
            )
        );
        return ResponseEntity.ok(authSession.response());
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<PasswordResetResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(passwordResetService.requestReset(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<PasswordResetResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(passwordResetService.resetPassword(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        authService.logout(resolveAccessToken(request), tokenCookieService.resolveRefreshToken(request));
        response.addHeader("Set-Cookie", tokenCookieService.clearRefreshCookieValue());
        return ResponseEntity.noContent().build();
    }

    private String resolveRefreshToken(HttpServletRequest request, RefreshTokenRequest body) {
        String cookieRefreshToken = tokenCookieService.resolveRefreshToken(request);
        if (StringUtils.hasText(cookieRefreshToken)) {
            return cookieRefreshToken;
        }
        return body == null ? null : body.refreshToken();
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}
