package com.bioqc.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bioqc.config.SecurityConfig;
import com.bioqc.dto.request.HematologyMeasurementRequest;
import com.bioqc.dto.request.HematologyParameterRequest;
import com.bioqc.dto.response.HematologyMeasurementResponse;
import com.bioqc.dto.response.HematologyParameterResponse;
import com.bioqc.entity.HematologyBioRecord;
import com.bioqc.exception.GlobalExceptionHandler;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.security.AccessTokenBlacklistService;
import com.bioqc.security.JwtAuthFilter;
import com.bioqc.service.HematologyQcService;
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

@WebMvcTest(HematologyController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, HematologyControllerTest.NoOpJwtFilterConfig.class})
class HematologyControllerTest {

    private static final String TEST_JWT_SECRET = "testsecretkeythatisfarlongerthanthirtytwobytesforjwt";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StubHematologyQcService hematologyQcService;

    @Test
    @DisplayName("deve retornar 201 com rastreabilidade do parâmetro ao registrar medição")
    void shouldReturn201WithParameterTraceabilityWhenCreatingMeasurement() throws Exception {
        hematologyQcService.createMeasurementResponse = measurementResponse("APROVADO");

        mockMvc.perform(post("/api/hematology/measurements")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validMeasurementRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.parameterId").exists())
            .andExpect(jsonPath("$.parameterEquipamento").value("Sysmex XN-1000"))
            .andExpect(jsonPath("$.parameterLoteControle").value("LOTE-H01"))
            .andExpect(jsonPath("$.parameterNivelControle").value("Normal"))
            .andExpect(jsonPath("$.status").value("APROVADO"))
            .andExpect(jsonPath("$.modoUsado").value("INTERVALO"));
    }

    @Test
    @DisplayName("deve retornar 201 ao criar parâmetro de hematologia")
    void shouldReturn201WhenCreatingParameter() throws Exception {
        hematologyQcService.createParameterResponse = parameterResponse();

        mockMvc.perform(post("/api/hematology/parameters")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validParameterRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.analito").value("RBC"))
            .andExpect(jsonPath("$.modo").value("INTERVALO"))
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("deve retornar lista de parâmetros como DTOs")
    void shouldReturnParameterListAsDtos() throws Exception {
        hematologyQcService.parameterList = List.of(parameterResponse());

        mockMvc.perform(get("/api/hematology/parameters")
                .with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].analito").value("RBC"))
            .andExpect(jsonPath("$[0].equipamento").value("Sysmex XN-1000"));
    }

    @Test
    @DisplayName("deve retornar lista de medições com rastreabilidade")
    void shouldReturnMeasurementListWithTraceability() throws Exception {
        hematologyQcService.measurementList = List.of(measurementResponse("APROVADO"));

        mockMvc.perform(get("/api/hematology/measurements")
                .with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].parameterId").exists())
            .andExpect(jsonPath("$[0].parameterEquipamento").value("Sysmex XN-1000"));
    }

    @Test
    @DisplayName("deve exigir autenticação para registrar medição")
    void shouldRequireAuthenticationForMeasurement() throws Exception {
        mockMvc.perform(post("/api/hematology/measurements")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validMeasurementRequest())))
            .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class NoOpJwtFilterConfig {
        @Bean
        StubHematologyQcService stubHematologyQcService() {
            return new StubHematologyQcService();
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

    static class StubHematologyQcService extends HematologyQcService {
        private HematologyMeasurementResponse createMeasurementResponse;
        private HematologyParameterResponse createParameterResponse;
        private List<HematologyParameterResponse> parameterList = List.of();
        private List<HematologyMeasurementResponse> measurementList = List.of();

        StubHematologyQcService() {
            super(null, null, null);
        }

        @Override
        public HematologyMeasurementResponse createMeasurement(
            com.bioqc.dto.request.HematologyMeasurementRequest request
        ) {
            return createMeasurementResponse;
        }

        @Override
        public HematologyParameterResponse createParameter(
            com.bioqc.dto.request.HematologyParameterRequest request
        ) {
            return createParameterResponse;
        }

        @Override
        public List<HematologyParameterResponse> getParameters(String analito) {
            return parameterList;
        }

        @Override
        public List<HematologyMeasurementResponse> getMeasurements(UUID parameterId) {
            return measurementList;
        }

        @Override
        public List<HematologyBioRecord> getBioRecords() {
            return List.of();
        }
    }

    private HematologyMeasurementRequest validMeasurementRequest() {
        return new HematologyMeasurementRequest(
            UUID.randomUUID(),
            LocalDate.of(2026, 4, 4),
            "RBC",
            4.5,
            "Controle nominal"
        );
    }

    private HematologyParameterRequest validParameterRequest() {
        return new HematologyParameterRequest(
            "RBC", "Sysmex XN-1000", "LOTE-H01", "Normal", "INTERVALO",
            4.5, 4.0, 5.0, 0.0
        );
    }

    private HematologyMeasurementResponse measurementResponse(String status) {
        return new HematologyMeasurementResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Sysmex XN-1000",
            "LOTE-H01",
            "Normal",
            LocalDate.of(2026, 4, 4),
            "RBC",
            4.5,
            "INTERVALO",
            4.0,
            5.0,
            status,
            "Controle nominal",
            Instant.now()
        );
    }

    private HematologyParameterResponse parameterResponse() {
        return new HematologyParameterResponse(
            UUID.randomUUID(),
            "RBC",
            "Sysmex XN-1000",
            "LOTE-H01",
            "Normal",
            "INTERVALO",
            4.5,
            4.0,
            5.0,
            0.0,
            true,
            Instant.now(),
            Instant.now()
        );
    }
}
