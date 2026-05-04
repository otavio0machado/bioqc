package com.bioqc.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bioqc.config.SecurityConfig;
import com.bioqc.dto.request.AreaQcMeasurementRequest;
import com.bioqc.dto.response.AreaQcMeasurementResponse;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.GlobalExceptionHandler;
import com.bioqc.security.AccessTokenBlacklistService;
import com.bioqc.security.JwtAuthFilter;
import com.bioqc.service.AreaQcService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AreaQcController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AreaQcControllerTest.NoOpJwtFilterConfig.class})
class AreaQcControllerTest {

    private static final String TEST_JWT_SECRET = "testsecretkeythatisfarlongerthanthirtytwobytesforjwt";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StubAreaQcService areaQcService;

    @Test
    @DisplayName("deve retornar 201 ao registrar medição da área com parâmetro rastreável")
    void shouldReturn201WhenCreatingAreaMeasurement() throws Exception {
        areaQcService.createMeasurementResponse = measurementResponse();

        mockMvc.perform(post("/api/qc/areas/imunologia/measurements")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.parameterId").value(areaQcService.createMeasurementResponse.parameterId().toString()))
            .andExpect(jsonPath("$.parameterEquipamento").value("EQ-1"))
            .andExpect(jsonPath("$.status").value("APROVADO"));
    }

    @Test
    @DisplayName("deve retornar 400 quando a medição da área encontra ambiguidade de parâmetro")
    void shouldReturn400WhenAreaMeasurementHasParameterConflict() throws Exception {
        areaQcService.createMeasurementException = new BusinessException(
            "Mais de um parâmetro ativo é compatível com a medição informada. Selecione explicitamente o parâmetro correto ou refine equipamento, lote e nível."
        );

        mockMvc.perform(post("/api/qc/areas/imunologia/measurements")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(areaQcService.createMeasurementException.getMessage()));
    }

    @Test
    @DisplayName("deve exigir autenticação para registrar medição da área")
    void shouldRequireAuthenticationForAreaMeasurement() throws Exception {
        mockMvc.perform(post("/api/qc/areas/imunologia/measurements")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class NoOpJwtFilterConfig {
        @Bean
        StubAreaQcService stubAreaQcService() {
            return new StubAreaQcService();
        }

        @Bean
        io.micrometer.core.instrument.MeterRegistry meterRegistry() {
            return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        }

        @Bean
        com.bioqc.security.JwtTokenProvider jwtTokenProvider() {
            return new com.bioqc.security.JwtTokenProvider(TEST_JWT_SECRET, "test-issuer", 900_000, 604_800_000);
        }

        @Bean
        AccessTokenBlacklistService accessTokenBlacklistService() {
            return new AccessTokenBlacklistService();
        }

        @Bean
        JwtAuthFilter jwtAuthFilter(
            com.bioqc.security.JwtTokenProvider jwtTokenProvider,
            AccessTokenBlacklistService accessTokenBlacklistService
        ) {
            return new JwtAuthFilter(jwtTokenProvider, accessTokenBlacklistService);
        }
    }

    static class StubAreaQcService extends AreaQcService {
        private AreaQcMeasurementResponse createMeasurementResponse;
        private RuntimeException createMeasurementException;

        StubAreaQcService() {
            super(null, null);
        }

        @Override
        public AreaQcMeasurementResponse createMeasurement(String area, AreaQcMeasurementRequest request) {
            if (createMeasurementException != null) {
                throw createMeasurementException;
            }
            return createMeasurementResponse;
        }

        @Override
        public List<com.bioqc.dto.response.AreaQcParameterResponse> getParameters(String area, String analito) {
            return List.of();
        }

        @Override
        public List<AreaQcMeasurementResponse> getMeasurements(String area, String analito, LocalDate startDate, LocalDate endDate) {
            return List.of();
        }
    }

    private AreaQcMeasurementRequest validRequest() {
        return new AreaQcMeasurementRequest(
            LocalDate.of(2026, 4, 4),
            "HIV",
            1.0D,
            UUID.randomUUID(),
            "EQ-1",
            "L1",
            "N1",
            "Controle nominal"
        );
    }

    private AreaQcMeasurementResponse measurementResponse() {
        return new AreaQcMeasurementResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "EQ-1",
            "L1",
            "N1",
            "imunologia",
            LocalDate.of(2026, 4, 4),
            "HIV",
            1.0D,
            "INTERVALO",
            0.9D,
            1.1D,
            "APROVADO",
            "Controle nominal",
            Instant.now()
        );
    }
}
