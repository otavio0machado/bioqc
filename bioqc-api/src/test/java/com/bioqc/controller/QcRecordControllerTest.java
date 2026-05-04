package com.bioqc.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bioqc.config.SecurityConfig;
import com.bioqc.dto.request.PostCalibrationRequest;
import com.bioqc.dto.response.QcRecordResponse;
import com.bioqc.entity.PostCalibrationRecord;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.GlobalExceptionHandler;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.entity.QcRecord;
import com.bioqc.security.AccessTokenBlacklistService;
import com.bioqc.security.JwtAuthFilter;
import com.bioqc.service.PostCalibrationService;
import com.bioqc.service.QcService;
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

@WebMvcTest(QcRecordController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, QcRecordControllerTest.NoOpJwtFilterConfig.class})
class QcRecordControllerTest {

    private static final String TEST_JWT_SECRET = "testsecretkeythatisfarlongerthanthirtytwobytesforjwt";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StubQcService qcService;

    @Autowired
    private StubPostCalibrationService postCalibrationService;

    @Test
    @DisplayName("deve retornar 200 ao listar registros")
    void shouldReturn200WhenGettingRecords() throws Exception {
        qcService.records = List.of(response());

        mockMvc.perform(get("/api/qc/records").with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].examName").value("Glicose"));
    }

    @Test
    @DisplayName("deve retornar 201 ao criar registro")
    void shouldReturn201WhenCreatingRecord() throws Exception {
        qcService.createResponse = response();

        mockMvc.perform(post("/api/qc/records")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("APROVADO"));
    }

    @Test
    @DisplayName("deve retornar 400 quando request é inválido")
    void shouldReturn400WhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/qc/records")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deve retornar 404 quando registro não existe")
    void shouldReturn404WhenRecordNotFound() throws Exception {
        qcService.recordException = new ResourceNotFoundException("Registro não encontrado");

        mockMvc.perform(get("/api/qc/records/" + UUID.randomUUID()).with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deve retornar 401 quando não autenticado")
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/qc/records"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deve retornar 201 ao registrar pós-calibração")
    void shouldReturn201WhenCreatingPostCalibration() throws Exception {
        postCalibrationService.createResponse = PostCalibrationRecord.builder()
            .id(UUID.randomUUID())
            .qcRecord(QcRecord.builder().id(UUID.randomUUID()).build())
            .date(LocalDate.now())
            .examName("Glicose")
            .originalValue(112D)
            .originalCv(12D)
            .postCalibrationValue(101D)
            .postCalibrationCv(1D)
            .targetValue(100D)
            .analyst("Ana")
            .notes("Recalibração ok")
            .build();

        mockMvc.perform(post("/api/qc/records/" + UUID.randomUUID() + "/post-calibration")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new PostCalibrationRequest(LocalDate.now(), 101D, "Ana", "Recalibração ok"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.examName").value("Glicose"))
            .andExpect(jsonPath("$.postCalibrationValue").value(101D));
    }

    @Test
    @DisplayName("deve retornar 400 quando pós-calibração viola regra de negócio")
    void shouldReturn400WhenPostCalibrationViolatesBusinessRule() throws Exception {
        postCalibrationService.createException = new BusinessException("A pós-calibração só pode ser registrada quando existe pendência corretiva ativa no registro de CQ.");

        mockMvc.perform(post("/api/qc/records/" + UUID.randomUUID() + "/post-calibration")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new PostCalibrationRequest(LocalDate.now(), 101D, "Ana", "Recalibração ok"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("A pós-calibração só pode ser registrada quando existe pendência corretiva ativa no registro de CQ."));
    }

    @TestConfiguration
    static class NoOpJwtFilterConfig {
        @Bean
        StubQcService stubQcService() {
            return new StubQcService();
        }

        @Bean
        StubPostCalibrationService postCalibrationService() {
            return new StubPostCalibrationService();
        }

        @Bean
        com.bioqc.service.QcBatchImportService qcBatchImportService() {
            // Nao usado pelos testes existentes, mas o controller exige bean.
            return new com.bioqc.service.QcBatchImportService(null, null, null) {
                @Override
                public com.bioqc.dto.response.BatchImportResult importPartial(
                    java.util.List<com.bioqc.dto.request.QcRecordRequest> requests,
                    org.springframework.security.core.Authentication authentication
                ) {
                    return new com.bioqc.dto.response.BatchImportResult(
                        java.util.UUID.randomUUID(), "PARTIAL", 0, 0, 0, java.util.List.of()
                    );
                }

                @Override
                public com.bioqc.dto.response.BatchImportResult importAtomic(
                    java.util.List<com.bioqc.dto.request.QcRecordRequest> requests,
                    org.springframework.security.core.Authentication authentication
                ) {
                    return new com.bioqc.dto.response.BatchImportResult(
                        java.util.UUID.randomUUID(), "ATOMIC", 0, 0, 0, java.util.List.of()
                    );
                }
            };
        }

        @Bean
        com.bioqc.service.ImportRunService importRunService() {
            return new com.bioqc.service.ImportRunService(null) {
                @Override
                public java.util.List<com.bioqc.dto.response.ImportRunResponse> history(int limit) {
                    return java.util.List.of();
                }
            };
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

    static class StubPostCalibrationService extends PostCalibrationService {
        private PostCalibrationRecord createResponse;
        private RuntimeException createException;

        StubPostCalibrationService() {
            super(null, null);
        }

        @Override
        public PostCalibrationRecord createPostCalibration(UUID qcRecordId, PostCalibrationRequest request) {
            if (createException != null) {
                throw createException;
            }
            return createResponse;
        }
    }

    static class StubQcService extends QcService {
        private List<QcRecordResponse> records = List.of();
        private QcRecordResponse createResponse;
        private RuntimeException recordException;

        StubQcService() {
            super(null, null, new com.bioqc.service.WestgardEngine(), null,
                new com.bioqc.service.AuditService(null, null, new com.fasterxml.jackson.databind.ObjectMapper()),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                null);
        }

        @Override
        public List<QcRecordResponse> getRecords(String area, String examName, LocalDate startDate, LocalDate endDate) {
            return records;
        }

        @Override
        public QcRecordResponse createRecord(com.bioqc.dto.request.QcRecordRequest request) {
            return createResponse;
        }

        @Override
        public QcRecordResponse getRecord(UUID id) {
            if (recordException != null) {
                throw recordException;
            }
            return createResponse;
        }
    }

    private QcRecordResponse response() {
        return new QcRecordResponse(
            UUID.randomUUID(), null, "Glicose", "bioquimica", LocalDate.now(), "Normal", "L1",
            100D, 100D, 5D, 5D, 10D, 1D, "AU680", "Ana", "APROVADO", false, List.of(),
            Instant.now(), Instant.now(), null, null, null, null
        );
    }

    private Object validRequest() {
        return new Object() {
            public final String examName = "Glicose";
            public final String area = "bioquimica";
            public final String date = LocalDate.now().toString();
            public final String level = "Normal";
            public final String lotNumber = "L1";
            public final double value = 100D;
            public final double targetValue = 100D;
            public final double targetSd = 5D;
            public final double cvLimit = 10D;
            public final String equipment = "AU680";
            public final String analyst = "Ana";
        };
    }
}
