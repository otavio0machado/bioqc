package com.bioqc.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bioqc.config.SecurityConfig;
import com.bioqc.dto.response.AuthResponse;
import com.bioqc.dto.response.PasswordResetResponse;
import com.bioqc.dto.response.UserResponse;
import com.bioqc.entity.Role;
import com.bioqc.exception.GlobalExceptionHandler;
import com.bioqc.filter.RateLimitFilter;
import com.bioqc.security.AccessTokenBlacklistService;
import com.bioqc.security.JwtAuthFilter;
import com.bioqc.security.TokenCookieService;
import com.bioqc.service.AuthService;
import com.bioqc.service.PasswordResetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AuthControllerTest.NoOpJwtFilterConfig.class})
class AuthControllerTest {

    private static final String TEST_JWT_SECRET = "testsecretkeythatisfarlongerthanthirtytwobytesforjwt";
    private static final String TEST_JWT_ISSUER = "test-issuer";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StubAuthService authService;

    @Autowired
    private StubPasswordResetService passwordResetService;

    @Test
    @DisplayName("deve retornar 200 em login bem-sucedido")
    void shouldReturn200OnSuccessfulLogin() throws Exception {
        authService.authSession = authSession();

        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new LoginBody())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access"))
            .andExpect(header().string("Set-Cookie", Matchers.containsString("refresh_token=refresh")));
    }

    @Test
    @DisplayName("deve retornar 400 em login inválido")
    void shouldReturn400OnInvalidLoginRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deve retornar 200 em refresh")
    void shouldReturn200OnTokenRefresh() throws Exception {
        authService.authSession = authSession();

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", "refresh"))
                .contentType("application/json")
                .content("{\"refreshToken\":\"refresh\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access"))
            .andExpect(header().string("Set-Cookie", Matchers.containsString("refresh_token=refresh")));
    }

    @Test
    @DisplayName("deve retornar 201 em register com admin")
    void shouldReturn201OnRegister() throws Exception {
        authService.userResponse = userResponse();

        mockMvc.perform(post("/api/auth/register")
                .with(user("admin").roles("ADMIN"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new RegisterBody())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("novo"));
    }

    @Test
    @DisplayName("deve retornar 201 em register com bearer token de admin")
    void shouldReturn201OnRegisterWithAdminBearerToken() throws Exception {
        authService.userResponse = userResponse();
        String adminAccessToken = tokenProvider().generateAccessToken(adminUser());

        mockMvc.perform(post("/api/auth/register")
                .header("Authorization", "Bearer " + adminAccessToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new RegisterBody())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("novo"));
    }

    @Test
    @DisplayName("deve retornar 403 em register sem admin")
    void shouldReturn403OnRegisterWithoutAdminRole() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .with(user("viewer").roles("VISUALIZADOR"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new RegisterBody())))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("deve retornar 401 em register com refresh token")
    void shouldReturn401OnRegisterWithRefreshToken() throws Exception {
        String refreshToken = tokenProvider().generateRefreshToken(adminUser(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/auth/register")
                .header("Authorization", "Bearer " + refreshToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new RegisterBody())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deve retornar 200 em solicitação de recuperação de senha")
    void shouldReturn200OnForgotPassword() throws Exception {
        passwordResetService.passwordResetResponse =
            new PasswordResetResponse("mensagem", "http://localhost:5173/reset-password?token=abc");

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType("application/json")
                .content("{\"email\":\"ana@bio.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("mensagem"));
    }

    @Test
    @DisplayName("deve retornar 200 em redefinição de senha")
    void shouldReturn200OnResetPassword() throws Exception {
        passwordResetService.passwordResetResponse =
            new PasswordResetResponse("Senha redefinida com sucesso.", null);

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType("application/json")
                .content("{\"token\":\"abc\",\"newPassword\":\"Senha123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Senha redefinida com sucesso."));
    }

    @Test
    @DisplayName("deve retornar 204 em logout e limpar cookie")
    void shouldReturn204OnLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .with(user("admin").roles("ADMIN"))
                .cookie(new Cookie("refresh_token", "refresh"))
                .header("Authorization", "Bearer access"))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Set-Cookie", Matchers.containsString("Max-Age=0")));
    }

    @TestConfiguration
    static class NoOpJwtFilterConfig {
        @Bean
        StubAuthService stubAuthService() {
            return new StubAuthService();
        }

        @Bean
        StubPasswordResetService stubPasswordResetService() {
            return new StubPasswordResetService();
        }

        @Bean
        RateLimitFilter rateLimitFilter() {
            return new RateLimitFilter(
                Jackson2ObjectMapperBuilder.json().build(),
                10,
                60,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
            );
        }

        @Bean
        AccessTokenBlacklistService accessTokenBlacklistService() {
            return new AccessTokenBlacklistService();
        }

        @Bean
        com.bioqc.security.JwtTokenProvider jwtTokenProvider() {
            return tokenProvider();
        }

        @Bean
        TokenCookieService tokenCookieService() {
            return new TokenCookieService("refresh_token", "/api/auth", "", false, "Lax");
        }

        @Bean
        JwtAuthFilter jwtAuthFilter(
            com.bioqc.security.JwtTokenProvider jwtTokenProvider,
            AccessTokenBlacklistService accessTokenBlacklistService
        ) {
            return new JwtAuthFilter(jwtTokenProvider, accessTokenBlacklistService);
        }
    }

    static class StubAuthService extends AuthService {
        private IssuedAuthSession authSession;
        private UserResponse userResponse;

        StubAuthService() {
            super(
                null,
                null,
                tokenProvider(),
                null,
                new AccessTokenBlacklistService(),
                new com.bioqc.service.AuditService(null, null, new com.fasterxml.jackson.databind.ObjectMapper())
            );
        }

        @Override
        public IssuedAuthSession login(com.bioqc.dto.request.LoginRequest request) {
            return authSession;
        }

        @Override
        public IssuedAuthSession refreshToken(String refreshToken) {
            return authSession;
        }

        @Override
        public UserResponse register(com.bioqc.dto.request.RegisterRequest request) {
            return userResponse;
        }
    }

    static class StubPasswordResetService extends PasswordResetService {
        private PasswordResetResponse passwordResetResponse;

        StubPasswordResetService() {
            super(null, null, null, null);
        }

        @Override
        public PasswordResetResponse requestReset(com.bioqc.dto.request.ForgotPasswordRequest request) {
            return passwordResetResponse;
        }

        @Override
        public PasswordResetResponse resetPassword(com.bioqc.dto.request.ResetPasswordRequest request) {
            return passwordResetResponse;
        }
    }

    private AuthService.IssuedAuthSession authSession() {
        return new AuthService.IssuedAuthSession(new AuthResponse("access", null, userResponse()), "refresh");
    }

    private UserResponse userResponse() {
        return new UserResponse(UUID.randomUUID(), "novo", "novo@bio.com", "Novo", "ADMIN", true, List.of());
    }

    private static com.bioqc.security.JwtTokenProvider tokenProvider() {
        return new com.bioqc.security.JwtTokenProvider(
            TEST_JWT_SECRET,
            TEST_JWT_ISSUER,
            900_000,
            604_800_000
        );
    }

    private com.bioqc.entity.User adminUser() {
        return com.bioqc.entity.User.builder()
            .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .username("admin")
            .email("admin@bio.com")
            .name("Admin")
            .role(Role.ADMIN)
            .permissions(Set.of())
            .isActive(true)
            .build();
    }

    private static final class LoginBody {
        public final String username = "admin";
        public final String password = "eva123";
    }

    private static final class RegisterBody {
        public final String username = "novo";
        public final String password = "nova1234";
        public final String name = "Novo";
        public final String role = "ADMIN";
    }
}
