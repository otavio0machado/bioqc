package com.bioqc.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bioqc.config.ReportsV2Properties;
import com.bioqc.config.SecurityConfig;
import com.bioqc.dto.reports.v2.GenerateReportV2Request;
import com.bioqc.dto.reports.v2.PreviewReportV2Request;
import com.bioqc.dto.reports.v2.PreviewResponse;
import com.bioqc.dto.reports.v2.ReportDefinitionResponse;
import com.bioqc.dto.reports.v2.ReportExecutionResponse;
import com.bioqc.dto.reports.v2.SignReportV2Request;
import com.bioqc.dto.reports.v2.VerifyReportResponse;
import com.bioqc.exception.GlobalExceptionHandler;
import com.bioqc.exception.ReportsV2ExceptionHandler;
import com.bioqc.security.AccessTokenBlacklistService;
import com.bioqc.security.JwtAuthFilter;
import com.bioqc.security.JwtTokenProvider;
import com.bioqc.service.reports.v2.InMemoryRateLimiter;
import com.bioqc.service.reports.v2.InvalidFilterException;
import com.bioqc.service.reports.v2.ReportAlreadySignedException;
import com.bioqc.service.reports.v2.ReportServiceV2;
import com.bioqc.service.reports.v2.catalog.ReportCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportV2Controller.class)
@Import({
    SecurityConfig.class,
    GlobalExceptionHandler.class,
    ReportsV2ExceptionHandler.class,
    ReportV2ControllerTest.Config.class
})
@TestPropertySource(properties = {
    "reports.v2.enabled=true",
    "reports.v2.public-base-url=http://localhost:5173",
    "reports.v2.storage.dir=${java.io.tmpdir}/bioqc-v2-test"
})
class ReportV2ControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StubReportServiceV2 service;
    @Autowired InMemoryRateLimiter rateLimiter;

    @BeforeEach
    void reset() {
        service.reset();
        // reconstroi o bucket zerando o estado — metodo package-private
        try {
            var clear = InMemoryRateLimiter.class.getDeclaredMethod("clear");
            clear.setAccessible(true);
            clear.invoke(rateLimiter);
        } catch (Exception ex) {
            // se reflection falhar, testes seriam pegajosos mas nao quebram ambiente
        }
    }

    @Test
    @DisplayName("GET /catalog 200 retorna lista para ADMIN")
    void catalogOkForAdmin() throws Exception {
        mockMvc.perform(get("/api/reports/v2/catalog").with(user("ana").roles("ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /catalog exige autenticacao")
    void catalogRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/reports/v2/catalog"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /generate com filtros invalidos retorna 422 + ProblemDetail")
    void generateInvalidFilters422() throws Exception {
        service.nextGenerateThrows = new InvalidFilterException(List.of("Filtro 'area' e obrigatorio"));
        mockMvc.perform(post("/api/reports/v2/generate")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("code", "CQ_OPERATIONAL_V2")))
                .with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    @DisplayName("POST /generate 201 + response")
    void generateCreated() throws Exception {
        ReportExecutionResponse response = new ReportExecutionResponse(
            UUID.randomUUID(), "CQ_OPERATIONAL_V2", "PDF", "SUCCESS",
            "BIO-202604-000001", "a".repeat(64), null, null, 100L, 1, "ana",
            Instant.now(), null, Instant.now().plusSeconds(3600),
            "/api/reports/v2/executions/xxx/download",
            "http://localhost:5173/r/verify/abc",
            "Abril/2026",
            List.of()
        );
        service.nextGenerateResponse = response;
        mockMvc.perform(post("/api/reports/v2/generate")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", "CQ_OPERATIONAL_V2",
                    "filters", Map.of("area", "bioquimica", "periodType", "current-month")
                )))
                .with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reportNumber").value("BIO-202604-000001"));
    }

    @Test
    @DisplayName("POST /preview retorna HTML")
    void previewOk() throws Exception {
        service.nextPreviewResponse = new PreviewResponse("<div>preview</div>", List.of(), "Abril/2026");
        mockMvc.perform(post("/api/reports/v2/preview")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", "CQ_OPERATIONAL_V2",
                    "filters", Map.of("area", "bioquimica", "periodType", "current-month")
                )))
                .with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.html").value("<div>preview</div>"));
    }

    @Test
    @DisplayName("POST /sign FUNCIONARIO -> 403")
    void signRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/reports/v2/executions/" + UUID.randomUUID() + "/sign")
                .with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /sign duplo -> 409 ja assinado")
    void signConflict() throws Exception {
        service.nextSignThrows = new ReportAlreadySignedException("ja assinado");
        mockMvc.perform(post("/api/reports/v2/executions/" + UUID.randomUUID() + "/sign")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /verify/{hash} publico (sem auth) — hash invalido -> 200 valid=false (Ressalva 5)")
    void verifyPublicHashInvalidoRetorna200ValidFalse() throws Exception {
        service.nextVerifyResponse = new VerifyReportResponse(
            null, null, null, null, null, null, null, null, null, false, false
        );
        mockMvc.perform(get("/api/reports/v2/verify/bogus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.signed").value(false));
    }

    @Test
    @DisplayName("GET /verify/{hash} valido retorna payload completo (Ressalva 5)")
    void verifyOk() throws Exception {
        service.nextVerifyResponse = new VerifyReportResponse(
            "BIO-202604-000001", "CQ_OPERATIONAL_V2", "Abril/2026",
            Instant.now(), "ana", "a".repeat(64), null, null, null, false, true
        );
        mockMvc.perform(get("/api/reports/v2/verify/abc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reportNumber").value("BIO-202604-000001"))
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.generatedByName").value("ana"))
            .andExpect(jsonPath("$.sha256").value("a".repeat(64)));
    }

    @Test
    @DisplayName("rate limit /verify: 11 requests em 1 min -> 11a 429 + Retry-After")
    void verifyRateLimit() throws Exception {
        service.nextVerifyResponse = new VerifyReportResponse(
            "BIO", "CODE", null, Instant.now(), null, null, null, null, null, false, true
        );
        // 10 primeiras passam (limite default)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/reports/v2/verify/somehash-" + i))
                .andExpect(status().isOk());
        }
        // 11a deve 429
        mockMvc.perform(get("/api/reports/v2/verify/overlimit"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }

    // ---------- Stub manual de ReportServiceV2 ----------

    static class StubReportServiceV2 extends ReportServiceV2 {
        ReportExecutionResponse nextGenerateResponse;
        RuntimeException nextGenerateThrows;
        PreviewResponse nextPreviewResponse;
        RuntimeException nextPreviewThrows;
        ReportExecutionResponse nextSignResponse;
        RuntimeException nextSignThrows;
        VerifyReportResponse nextVerifyResponse;
        RuntimeException nextVerifyThrows;
        AtomicInteger verifyCalls = new AtomicInteger();

        StubReportServiceV2() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        void reset() {
            nextGenerateResponse = null;
            nextGenerateThrows = null;
            nextPreviewResponse = null;
            nextPreviewThrows = null;
            nextSignResponse = null;
            nextSignThrows = null;
            nextVerifyResponse = null;
            nextVerifyThrows = null;
            verifyCalls.set(0);
        }

        @Override
        public List<ReportDefinitionResponse> listCatalog(Authentication auth) {
            return List.of();
        }

        @Override
        public ReportExecutionResponse generate(GenerateReportV2Request request, Authentication auth) {
            if (nextGenerateThrows != null) throw nextGenerateThrows;
            return nextGenerateResponse;
        }

        @Override
        public PreviewResponse preview(PreviewReportV2Request request, Authentication auth) {
            if (nextPreviewThrows != null) throw nextPreviewThrows;
            return nextPreviewResponse;
        }

        @Override
        public ReportExecutionResponse sign(UUID executionId, SignReportV2Request request, Authentication auth) {
            if (nextSignThrows != null) throw nextSignThrows;
            return nextSignResponse;
        }

        @Override
        public VerifyReportResponse verify(String hash) {
            verifyCalls.incrementAndGet();
            if (nextVerifyThrows != null) throw nextVerifyThrows;
            return nextVerifyResponse;
        }

        @Override
        public Page<ReportExecutionResponse> listExecutions(
            ReportCode code, String status, Instant from, Instant to, Authentication auth, Pageable pageable
        ) {
            return Page.empty();
        }
    }

    @TestConfiguration
    static class Config {
        private static final String TEST_JWT_SECRET = "testsecretkeythatisfarlongerthanthirtytwobytesforjwt";

        @Bean StubReportServiceV2 reportServiceV2() { return new StubReportServiceV2(); }

        @Bean InMemoryRateLimiter rateLimiter() { return new InMemoryRateLimiter(); }

        @Bean io.micrometer.core.instrument.MeterRegistry meterRegistry() {
            return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        }

        @Bean com.bioqc.filter.RateLimitFilter rateLimitFilter(
            io.micrometer.core.instrument.MeterRegistry reg
        ) {
            return new com.bioqc.filter.RateLimitFilter(
                org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build(),
                10, 60, reg
            );
        }

        @Bean
        ReportsV2Properties reportsV2Properties() {
            ReportsV2Properties p = new ReportsV2Properties();
            p.setEnabled(true);
            p.setPublicBaseUrl("http://localhost:5173");
            ReportsV2Properties.Storage s = new ReportsV2Properties.Storage();
            s.setDir(System.getProperty("java.io.tmpdir") + "/bioqc-v2-test");
            p.setStorage(s);
            return p;
        }

        @Bean JwtTokenProvider jwtTokenProvider() {
            return new JwtTokenProvider(TEST_JWT_SECRET, "test-issuer", 900_000, 604_800_000);
        }

        @Bean AccessTokenBlacklistService accessTokenBlacklistService() {
            return new AccessTokenBlacklistService();
        }

        @Bean JwtAuthFilter jwtAuthFilter(JwtTokenProvider p, AccessTokenBlacklistService b) {
            return new JwtAuthFilter(p, b);
        }
    }
}
