package com.bioqc.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    @Test
    @DisplayName("gera novo correlationId quando header ausente e propaga em MDC + response")
    void generatesWhenMissing() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        final String[] insideMdc = new String[1];
        FilterChain chain = (r, s) -> insideMdc[0] = MDC.get("correlationId");
        filter.doFilter(req, res, chain);

        assertThat(insideMdc[0]).isNotBlank();
        assertThat(res.getHeader("X-Correlation-Id")).isEqualTo(insideMdc[0]);
        // Fora do filter, MDC foi limpo
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @DisplayName("respeita X-Correlation-Id do cliente quando presente")
    void respectsIncomingHeader() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CorrelationIdFilter.HEADER, "client-abc123");
        MockHttpServletResponse res = new MockHttpServletResponse();

        final String[] insideMdc = new String[1];
        FilterChain chain = (r, s) -> insideMdc[0] = MDC.get("correlationId");
        filter.doFilter(req, res, chain);

        assertThat(insideMdc[0]).isEqualTo("client-abc123");
        assertThat(res.getHeader("X-Correlation-Id")).isEqualTo("client-abc123");
    }

    @Test
    @DisplayName("sanitiza caracteres especiais do header (log injection)")
    void sanitizesInput() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CorrelationIdFilter.HEADER, "abc\n\r\tINJECT <script>");
        MockHttpServletResponse res = new MockHttpServletResponse();

        final String[] insideMdc = new String[1];
        FilterChain chain = (r, s) -> insideMdc[0] = MDC.get("correlationId");
        filter.doFilter(req, res, chain);

        // Sem caracteres de controle, sem < ou >
        assertThat(insideMdc[0]).doesNotContain("\n").doesNotContain("\r")
            .doesNotContain("\t").doesNotContain("<").doesNotContain(">");
        assertThat(insideMdc[0]).isEqualTo("abcINJECTscript");
    }
}
