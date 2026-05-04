package com.bioqc.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenCookieServiceTest {

    @Test
    void shouldCreateCrossSiteSecureRefreshCookie() {
        TokenCookieService service = new TokenCookieService(
            "refresh_token",
            "/api/auth",
            "",
            true,
            "None"
        );

        String cookie = service.createRefreshCookieValue("refresh-value", 1800);

        assertThat(cookie).contains("refresh_token=refresh-value");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("Secure");
        assertThat(cookie).contains("SameSite=None");
        assertThat(cookie).contains("Path=/api/auth");
    }
}
