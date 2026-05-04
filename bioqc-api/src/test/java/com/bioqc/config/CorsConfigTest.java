package com.bioqc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigTest {

    @Test
    void shouldMergeCorsOriginsWithFrontendUrl() {
        CorsConfigurationSource source = new CorsConfig().corsConfigurationSource(
            "https://bioqc-web-production.up.railway.app",
            "https://labbio.app"
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/login");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
            .containsExactlyInAnyOrder(
                "https://bioqc-web-production.up.railway.app",
                "https://labbio.app"
            );
    }

    @Test
    void shouldRejectWildcardOrigins() {
        CorsConfig corsConfig = new CorsConfig();

        assertThatThrownBy(() -> corsConfig.corsConfigurationSource("*", ""))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cors.allowed-origins");
    }
}
