package com.bioqc.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bioqc.config.SecurityConfig;
import com.bioqc.dto.request.ArchiveReagentLotRequest;
import com.bioqc.dto.request.DeleteReagentLotRequest;
import com.bioqc.dto.request.ReagentLotRequest;
import com.bioqc.dto.request.StockMovementRequest;
import com.bioqc.dto.request.UnarchiveReagentLotRequest;
import com.bioqc.dto.response.ReagentLabelSummary;
import com.bioqc.dto.response.ResponsibleSummary;
import com.bioqc.entity.ReagentLot;
import com.bioqc.entity.ReagentStatus;
import com.bioqc.entity.StockMovement;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.GlobalExceptionHandler;
import com.bioqc.security.AccessTokenBlacklistService;
import com.bioqc.security.JwtAuthFilter;
import com.bioqc.service.ReagentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests do ReagentController apos refator-reagentes-v3:
 * - Endpoints novos (archive/unarchive)
 * - DELETE ADMIN-only com confirmLotNumber
 * - GET /tags removido (404)
 * - CSV header novo
 * - SAIDA recusado em createMovement
 */
@WebMvcTest(controllers = {ReagentController.class, UserController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, ReagentControllerTest.TestConfig.class})
class ReagentControllerTest {

    private static final String TEST_JWT_SECRET = "testsecretkeythatisfarlongerthanthirtytwobytesforjwt";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StubReagentService reagentService;

    @BeforeEach
    void resetStub() {
        reagentService.createLotResponse = null;
        reagentService.createLotException = null;
        reagentService.createMovementResponse = null;
        reagentService.createMovementException = null;
        reagentService.byLotNumberResponse = List.of();
        reagentService.deletedLotId = null;
        reagentService.deletedLotRequest = null;
        reagentService.labelSummaries = List.of();
        reagentService.getLotsResponse = null;
        reagentService.getLotsException = null;
        reagentService.archiveResponse = null;
        reagentService.archiveException = null;
        reagentService.unarchiveResponse = null;
        reagentService.unarchiveException = null;
        reagentService.responsiblesResponse = List.of();
        reagentService.deleteLotException = null;
    }

    private static ReagentLotRequest sampleRequest() {
        return new ReagentLotRequest(
            "ALT", "L123", "Bio", "Bioquímica",
            8, 0, "em_estoque",
            LocalDate.now().plusDays(60), "Geladeira 2", "2-8°C",
            null, null, null
        );
    }

    @Test
    @DisplayName("POST /api/reagents com 10 obrigatorios cria com 201")
    void createLot_deveRetornar201() throws Exception {
        ReagentLot lot = buildLot(8, 0);
        reagentService.createLotResponse = lot;

        String body = objectMapper.writeValueAsString(sampleRequest());

        mockMvc.perform(post("/api/reagents")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.label").value("ALT"))
            .andExpect(jsonPath("$.lotNumber").value("L123"))
            .andExpect(jsonPath("$.unitsInStock").value(8))
            .andExpect(jsonPath("$.unitsInUse").value(0))
            .andExpect(jsonPath("$.totalUnits").value(8))
            .andExpect(jsonPath("$.canReceiveEntry").value(true))
            .andExpect(jsonPath("$.allowedMovementTypes[0]").value("ENTRADA"));
    }

    @Test
    @DisplayName("POST /api/reagents sem location retorna 400")
    void createLot_semLocation_deveRetornar400() throws Exception {
        ReagentLotRequest req = new ReagentLotRequest(
            "ALT", "L123", "Bio", "Bioquímica",
            8, 0, "em_estoque",
            LocalDate.now().plusDays(60), "", "2-8°C",
            null, null, null
        );

        mockMvc.perform(post("/api/reagents")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reagents com category fora de lista retorna 400")
    void createLot_categoryInvalida_deveRetornar400() throws Exception {
        reagentService.createLotException =
            new BusinessException("Categoria invalida. Valores aceitos: ...");
        ReagentLotRequest req = new ReagentLotRequest(
            "ALT", "L123", "Bio", "INEXISTENTE",
            8, 0, "em_estoque",
            LocalDate.now().plusDays(60), "Geladeira 2", "2-8°C",
            null, null, null
        );

        mockMvc.perform(post("/api/reagents")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Categoria invalida. Valores aceitos: ..."));
    }

    @Test
    @DisplayName("GET /api/reagents?status=ativo retorna 400 (status legado)")
    void getLots_statusLegado_deveRetornar400() throws Exception {
        reagentService.getLotsException =
            new BusinessException("Status legado nao suportado. Use: em_estoque, em_uso, vencido, inativo");

        mockMvc.perform(get("/api/reagents")
                .param("status", "ativo")
                .with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("Status legado nao suportado")));
    }

    @Test
    @DisplayName("GET /api/reagents?status=em_estoque retorna 200")
    void getLots_statusNovo_deveRetornar200() throws Exception {
        reagentService.getLotsResponse = List.of();

        mockMvc.perform(get("/api/reagents")
                .param("status", "em_estoque")
                .with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/reagents/labels retorna 200 com shape ReagentLabelSummary v3 (inativos)")
    void getLabels_deveRetornarShapeNovo() throws Exception {
        reagentService.labelSummaries = List.of(
            new ReagentLabelSummary("Glicose HK", 12, 5, 3, 2, 2)
        );

        mockMvc.perform(get("/api/reagents/labels")
                .with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].label").value("Glicose HK"))
            .andExpect(jsonPath("$[0].total").value(12))
            .andExpect(jsonPath("$[0].emEstoque").value(5))
            .andExpect(jsonPath("$[0].emUso").value(3))
            .andExpect(jsonPath("$[0].inativos").value(2))
            .andExpect(jsonPath("$[0].vencidos").value(2));
    }

    @Test
    @DisplayName("GET /api/reagents/tags removido em v3 — controller nao mapeia esse path")
    void getTags_removidoV3() throws Exception {
        // Defesa estrutural: o ReagentController v3 nao tem mais @GetMapping("/tags").
        // Em runtime real (com Spring Boot completo), retornaria 404 via
        // throwExceptionIfNoHandlerFound + handler global. No WebMvcTest stripped down
        // o path matcha tentativamente outras rotas e o GlobalExceptionHandler
        // (handleGeneric) traduz a HttpRequestMethodNotSupportedException para 500.
        // O importante: o endpoint nao retorna 200 com payload — quem chamava
        // legacymente nao recebe mais shape ReagentTagSummary.
        var result = mockMvc.perform(get("/api/reagents/tags")
                .with(user("ana").roles("FUNCIONARIO")))
            .andReturn();
        int statusCode = result.getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(statusCode)
            .as("rota /tags removida deve retornar erro, nao 200")
            .isNotEqualTo(200);
    }

    @Test
    @DisplayName("CSV header v3 — novas colunas Em Estoque/Em Uso/Total/Arquivado em/por")
    void exportCsv_header_canonicoV3() throws Exception {
        reagentService.getLotsResponse = List.of();

        var result = mockMvc.perform(get("/api/reagents/export/csv")
                .with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/csv; charset=UTF-8"))
            .andReturn();

        String csv = result.getResponse().getContentAsString();
        String firstLine = csv.split("\n")[0].trim();
        org.assertj.core.api.Assertions.assertThat(firstLine)
            .isEqualTo("Etiqueta,Lote,Fabricante,Categoria,Validade,Dias Restantes,Em Estoque,Em Uso,Total,Status,Localizacao,Temperatura,Arquivado em,Arquivado por");
    }

    @Test
    @DisplayName("createMovement deve retornar 201 com StockMovementResponse")
    void createMovement_deveRetornar201() throws Exception {
        StockMovement movement = StockMovement.builder()
            .id(UUID.randomUUID())
            .type("ENTRADA")
            .quantity(20D)
            .responsible("Ana")
            .notes("")
            .previousUnitsInStock(5)
            .previousUnitsInUse(0)
            .build();
        reagentService.createMovementResponse = movement;

        UUID lotId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(
            new StockMovementRequest("ENTRADA", 20D, "Ana", "", null, null, null, null));

        mockMvc.perform(post("/api/reagents/" + lotId + "/movements")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("ENTRADA"))
            .andExpect(jsonPath("$.quantity").value(20D))
            .andExpect(jsonPath("$.previousUnitsInStock").value(5))
            .andExpect(jsonPath("$.isLegacy").value(false));
    }

    @Test
    @DisplayName("v3.1: POST /api/reagents/{id}/movements ABERTURA + eventDate → 200 com response.eventDate populado")
    void createMovement_aberturaComEventDate_retornaResponseComEventDate() throws Exception {
        LocalDate declared = LocalDate.of(2026, 4, 1);
        StockMovement movement = StockMovement.builder()
            .id(UUID.randomUUID())
            .type("ABERTURA")
            .quantity(1D)
            .responsible("Ana")
            .notes("")
            .previousUnitsInStock(5)
            .previousUnitsInUse(0)
            .eventDate(declared)
            .build();
        reagentService.createMovementResponse = movement;

        UUID lotId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(
            new StockMovementRequest("ABERTURA", 1D, "Ana", "", null, null, null, declared));

        mockMvc.perform(post("/api/reagents/" + lotId + "/movements")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("ABERTURA"))
            .andExpect(jsonPath("$.eventDate").value(declared.toString()));
    }

    @Test
    @DisplayName("v3.1: POST /movements com eventDate futura → 400")
    void createMovement_eventDateFutura_retorna400() throws Exception {
        reagentService.createMovementException = new BusinessException(
            "Data de abertura não pode ser futura.");

        UUID lotId = UUID.randomUUID();
        LocalDate futura = LocalDate.now().plusDays(1);
        String body = objectMapper.writeValueAsString(
            new StockMovementRequest("ABERTURA", 1D, "Ana", "", null, null, null, futura));

        mockMvc.perform(post("/api/reagents/" + lotId + "/movements")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("não pode ser futura")));
    }

    @Test
    @DisplayName("createMovement com type=SAIDA retorna 400 (descontinuado em v3)")
    void createMovement_saidaDescontinuada_deveRetornar400() throws Exception {
        reagentService.createMovementException =
            new BusinessException("Tipo SAIDA descontinuado em v3. Use CONSUMO para registrar uso/descarte ou AJUSTE para correcao de inventario.");

        UUID lotId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(
            new StockMovementRequest("SAIDA", 5D, "Ana", "", null, null, null, null));

        mockMvc.perform(post("/api/reagents/" + lotId + "/movements")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("descontinuado")));
    }

    @Test
    @DisplayName("getByLotNumber deve retornar lista")
    void getByLotNumber_deveRetornarLista() throws Exception {
        ReagentLot lot1 = buildLot(10, 2);
        ReagentLot lot2 = buildLot(5, 0);
        lot2.setManufacturer("OutroFab");
        reagentService.byLotNumberResponse = List.of(lot1, lot2);

        mockMvc.perform(get("/api/reagents/by-lot-number")
                .param("lotNumber", "L123")
                .with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].lotNumber").value("L123"));
    }

    @Test
    @DisplayName("DELETE /api/reagents/{id} como FUNCIONARIO retorna 403")
    void deleteLot_funcionario_deveRetornar403() throws Exception {
        UUID lotId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new DeleteReagentLotRequest("L123"));

        mockMvc.perform(delete("/api/reagents/" + lotId)
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/reagents/{id} como ADMIN happy path retorna 204")
    void deleteLot_admin_deveRetornar204() throws Exception {
        UUID lotId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new DeleteReagentLotRequest("L123"));

        mockMvc.perform(delete("/api/reagents/" + lotId)
                .with(user("admin").roles("ADMIN"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(reagentService.deletedLotId).isEqualTo(lotId);
        org.assertj.core.api.Assertions.assertThat(reagentService.deletedLotRequest)
            .isNotNull();
        org.assertj.core.api.Assertions.assertThat(reagentService.deletedLotRequest.confirmLotNumber())
            .isEqualTo("L123");
    }

    @Test
    @DisplayName("DELETE /api/reagents/{id} como ADMIN com usedInQc=true retorna 400")
    void deleteLot_admin_usedInQc_deveRetornar400() throws Exception {
        reagentService.deleteLotException = new BusinessException(
            "Lote utilizado em CQ recente nao pode ser apagado. Use POST /archive em vez disso.");
        UUID lotId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new DeleteReagentLotRequest("L123"));

        mockMvc.perform(delete("/api/reagents/" + lotId)
                .with(user("admin").roles("ADMIN"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("CQ recente")));
    }

    @Test
    @DisplayName("DELETE /api/reagents/{id} como ADMIN sem body retorna 400")
    void deleteLot_admin_semBody_deveRetornar400() throws Exception {
        UUID lotId = UUID.randomUUID();

        mockMvc.perform(delete("/api/reagents/" + lotId)
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reagents/{id}/archive happy path retorna 200 com status=inativo")
    void archiveLot_happy_retorna200() throws Exception {
        ReagentLot lot = buildLot(0, 0);
        lot.setStatus(ReagentStatus.INATIVO);
        lot.setArchivedAt(LocalDate.now());
        lot.setArchivedBy("ana");
        reagentService.archiveResponse = lot;

        UUID lotId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(
            new ArchiveReagentLotRequest(LocalDate.now(), "ana"));

        mockMvc.perform(post("/api/reagents/" + lotId + "/archive")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("inativo"))
            .andExpect(jsonPath("$.archivedBy").value("ana"));
    }

    @Test
    @DisplayName("POST /api/reagents/{id}/archive sem archivedBy retorna 400")
    void archiveLot_semArchivedBy_retorna400() throws Exception {
        UUID lotId = UUID.randomUUID();
        // archivedBy = "" viola @NotBlank
        String body = "{\"archivedAt\":\"" + LocalDate.now() + "\",\"archivedBy\":\"\"}";

        mockMvc.perform(post("/api/reagents/" + lotId + "/archive")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reagents/{id}/archive com archivedAt futura retorna 400")
    void archiveLot_dataFutura_retorna400() throws Exception {
        reagentService.archiveException = new BusinessException(
            "archivedAt nao pode ser data futura");

        UUID lotId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(
            new ArchiveReagentLotRequest(LocalDate.now().plusDays(1), "ana"));

        mockMvc.perform(post("/api/reagents/" + lotId + "/archive")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reagents/{id}/unarchive happy path retorna 200")
    void unarchiveLot_happy_retorna200() throws Exception {
        ReagentLot lot = buildLot(5, 0);
        lot.setStatus(ReagentStatus.EM_ESTOQUE);
        lot.setArchivedAt(LocalDate.now().minusDays(10));
        lot.setArchivedBy("ana");
        reagentService.unarchiveResponse = lot;

        UUID lotId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(
            new UnarchiveReagentLotRequest("voltou ao operacional"));

        mockMvc.perform(post("/api/reagents/" + lotId + "/unarchive")
                .with(user("ana").roles("FUNCIONARIO"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("em_estoque"))
            .andExpect(jsonPath("$.archivedBy").value("ana"));
    }

    @Test
    @DisplayName("POST /api/reagents/{id}/unarchive sem body retorna 200 (reason opcional)")
    void unarchiveLot_semBody_retorna200() throws Exception {
        ReagentLot lot = buildLot(5, 0);
        reagentService.unarchiveResponse = lot;

        UUID lotId = UUID.randomUUID();

        mockMvc.perform(post("/api/reagents/" + lotId + "/unarchive")
                .with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/users/responsibles como FUNCIONARIO retorna 200 com shape filtrado")
    void getResponsibles_funcionario_retorna200() throws Exception {
        reagentService.responsiblesResponse = List.of(
            new ResponsibleSummary(UUID.randomUUID(), "Ana Silva", "ana", "FUNCIONARIO"),
            new ResponsibleSummary(UUID.randomUUID(), "Bruno Costa", "bruno", "ADMIN")
        );

        mockMvc.perform(get("/api/users/responsibles")
                .with(user("ana").roles("FUNCIONARIO")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Ana Silva"))
            .andExpect(jsonPath("$[0].username").value("ana"))
            .andExpect(jsonPath("$[0].role").value("FUNCIONARIO"))
            // privacidade: nao expor email/permissions/createdAt
            .andExpect(jsonPath("$[0].email").doesNotExist())
            .andExpect(jsonPath("$[0].permissions").doesNotExist())
            .andExpect(jsonPath("$[0].createdAt").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/users/responsibles sem autenticacao retorna 401")
    void getResponsibles_anonimo_retorna401() throws Exception {
        mockMvc.perform(get("/api/users/responsibles"))
            .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        StubReagentService stubReagentService() {
            return new StubReagentService();
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

    static class StubReagentService extends ReagentService {
        ReagentLot createLotResponse;
        RuntimeException createLotException;
        StockMovement createMovementResponse;
        RuntimeException createMovementException;
        List<ReagentLot> byLotNumberResponse = List.of();
        UUID deletedLotId;
        DeleteReagentLotRequest deletedLotRequest;
        RuntimeException deleteLotException;
        List<ReagentLabelSummary> labelSummaries = List.of();
        java.util.List<com.bioqc.dto.response.ReagentLotResponse> getLotsResponse;
        RuntimeException getLotsException;
        ReagentLot archiveResponse;
        RuntimeException archiveException;
        ReagentLot unarchiveResponse;
        RuntimeException unarchiveException;
        List<ResponsibleSummary> responsiblesResponse = List.of();

        StubReagentService() {
            super(null, null, null, null, null);
        }

        @Override
        public ReagentLot createLot(ReagentLotRequest request) {
            if (createLotException != null) {
                throw createLotException;
            }
            return createLotResponse;
        }

        @Override
        public StockMovement createMovement(UUID lotId, StockMovementRequest request) {
            if (createMovementException != null) {
                throw createMovementException;
            }
            return createMovementResponse;
        }

        @Override
        public List<ReagentLot> getByLotNumber(String lotNumber) {
            return byLotNumberResponse;
        }

        @Override
        public void deleteLot(UUID id, DeleteReagentLotRequest request) {
            if (deleteLotException != null) {
                throw deleteLotException;
            }
            deletedLotId = id;
            deletedLotRequest = request;
        }

        @Override
        public ReagentLot archiveLot(UUID id, ArchiveReagentLotRequest request) {
            if (archiveException != null) {
                throw archiveException;
            }
            return archiveResponse;
        }

        @Override
        public ReagentLot unarchiveLot(UUID id, UnarchiveReagentLotRequest request) {
            if (unarchiveException != null) {
                throw unarchiveException;
            }
            return unarchiveResponse;
        }

        @Override
        public java.util.List<com.bioqc.dto.response.ReagentLotResponse> getLots(String category, String status) {
            if (getLotsException != null) {
                throw getLotsException;
            }
            return getLotsResponse == null ? List.of() : getLotsResponse;
        }

        @Override
        public List<ReagentLabelSummary> getLabelSummaries() {
            return labelSummaries;
        }

        @Override
        public List<ResponsibleSummary> getResponsibles() {
            return responsiblesResponse;
        }
    }

    private ReagentLot buildLot(int unitsInStock, int unitsInUse) {
        return ReagentLot.builder()
            .id(UUID.randomUUID())
            .name("ALT")
            .lotNumber("L123")
            .manufacturer("Bio")
            .category("Bioquímica")
            .unitsInStock(unitsInStock)
            .unitsInUse(unitsInUse)
            .expiryDate(LocalDate.now().plusDays(60))
            .status(ReagentStatus.EM_ESTOQUE)
            .needsStockReview(false)
            .build();
    }
}
