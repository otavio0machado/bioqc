package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bioqc.dto.request.ArchiveReagentLotRequest;
import com.bioqc.dto.request.DeleteReagentLotRequest;
import com.bioqc.dto.request.ReagentLotRequest;
import com.bioqc.dto.request.StockMovementRequest;
import com.bioqc.dto.request.UnarchiveReagentLotRequest;
import com.bioqc.entity.ReagentLot;
import com.bioqc.entity.ReagentStatus;
import com.bioqc.entity.Role;
import com.bioqc.entity.StockMovement;
import com.bioqc.entity.User;
import com.bioqc.exception.BusinessException;
import com.bioqc.repository.QcRecordRepository;
import com.bioqc.repository.ReagentLotRepository;
import com.bioqc.repository.StockMovementRepository;
import com.bioqc.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Bateria do refator-reagentes-v3 (PR consolidado). Cobre:
 *  - Regra ternaria de {@code deriveStatus} v3 (drop fora_de_estoque, add inativo)
 *  - Estoque per-unit ({@code unitsInStock} + {@code unitsInUse})
 *  - Branches de {@code createMovement}: ENTRADA, ABERTURA, FECHAMENTO, CONSUMO, AJUSTE
 *  - SAIDA recusada em escrita (mantida em leitura/historico)
 *  - ABERTURA/FECHAMENTO recusam {@code quantity != 1}
 *  - CONSUMO em vencido exige reason
 *  - {@code archive} valida archivedBy=username (NAO name) com role elegivel
 *  - {@code unarchive} preserva archivedAt/archivedBy + re-deriva status
 *  - {@code deleteLot} hard delete cascade + audit snapshot enumerativo
 *  - {@code REAGENT_OPENED_DATE_DERIVED} (ABERTURA) vs {@code _BACKFILLED} (UPDATE)
 */
@ExtendWith(MockitoExtension.class)
class ReagentServiceTest {

    private ReagentService reagentService;

    @Mock
    private ReagentLotRepository reagentLotRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private QcRecordRepository qcRecordRepository;

    @Mock
    private UserRepository userRepository;

    private RecordingAuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new RecordingAuditService();
        reagentService = new ReagentService(
            reagentLotRepository, stockMovementRepository, qcRecordRepository,
            userRepository, auditService);
    }

    private static final class RecordingAuditService extends AuditService {
        record Call(String action, String entityType, UUID entityId, Map<String, Object> details) {}

        private final List<Call> calls = new ArrayList<>();

        RecordingAuditService() {
            super(null, null, new ObjectMapper());
        }

        @Override
        public void log(String action, String entityType, UUID entityId, Map<String, Object> details) {
            calls.add(new Call(action, entityType, entityId, details));
        }

        @Override
        public void log(String action, String entityType, UUID entityId) {
            log(action, entityType, entityId, null);
        }

        List<Call> callsFor(String action) {
            return calls.stream().filter(c -> c.action().equals(action)).toList();
        }
    }

    // ===== Helpers =====

    private ReagentLotRequest defaultRequest(String status) {
        return new ReagentLotRequest(
            "ALT", "L123", "Bio", "Bioquímica",
            8, 0, status,
            LocalDate.now().plusDays(60), "Geladeira 2", "2-8°C",
            null, null, null
        );
    }

    private ReagentLotRequest fullRequest(
        String label, String lotNumber, String manufacturer, String category,
        Integer unitsInStock, Integer unitsInUse, String status, LocalDate expiry,
        String location, String temp,
        String supplier, LocalDate received, LocalDate opened
    ) {
        return new ReagentLotRequest(
            label, lotNumber, manufacturer, category,
            unitsInStock, unitsInUse, status, expiry, location, temp,
            supplier, received, opened
        );
    }

    private ReagentLot lot(int stock, int use) {
        return ReagentLot.builder()
            .id(UUID.randomUUID())
            .name("ALT")
            .lotNumber("L123")
            .manufacturer("Bio")
            .unitsInStock(stock)
            .unitsInUse(use)
            .expiryDate(LocalDate.now().plusDays(60))
            .status(ReagentStatus.EM_ESTOQUE)
            .needsStockReview(false)
            .build();
    }

    // ===== createLot =====

    @Test
    @DisplayName("createLot cria com sucesso quando dados validos")
    void shouldCreateLotSuccessfully() {
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));

        ReagentLot lot = reagentService.createLot(defaultRequest("em_estoque"));

        assertThat(lot.getName()).isEqualTo("ALT");
        assertThat(lot.getStatus()).isEqualTo(ReagentStatus.EM_ESTOQUE);
        assertThat(lot.getUnitsInStock()).isEqualTo(8);
        assertThat(lot.getUnitsInUse()).isEqualTo(0);
    }

    @Test
    @DisplayName("createLot com status='inativo' rejeitado com 400 (decisao 1.4)")
    void createLot_statusInativo_rejeitado() {
        assertThatThrownBy(() -> reagentService.createLot(defaultRequest("inativo")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Status 'inativo' nao pode ser definido em CREATE/UPDATE");
    }

    @Test
    @DisplayName("createLot com expiryDate < hoje forca status=vencido")
    void createLot_expiryPassada_forcaVencido() {
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));

        ReagentLotRequest req = fullRequest(
            "ALT", "L-VENC", "Bio", "Bioquímica",
            10, 0, "em_estoque",
            LocalDate.now().minusDays(5),
            "Geladeira 2", "2-8°C",
            null, null, null
        );

        ReagentLot lot = reagentService.createLot(req);

        assertThat(lot.getStatus()).isEqualTo(ReagentStatus.VENCIDO);
    }

    @Test
    @DisplayName("createLot com em_uso e openedDate=null grava openedDate=hoje")
    void createLot_emUso_semOpenedDate_setaToday() {
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));

        ReagentLotRequest req = fullRequest(
            "ALT", "L-USO", "Bio", "Bioquímica",
            5, 1, "em_uso",
            LocalDate.now().plusDays(30),
            "Geladeira 2", "2-8°C",
            null, null, null
        );

        ReagentLot lot = reagentService.createLot(req);

        assertThat(lot.getStatus()).isEqualTo(ReagentStatus.EM_USO);
        assertThat(lot.getOpenedDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("createLot com (lotNumber, manufacturer) ja existente deve falhar")
    void createLot_comDuplicata_deveLancarException() {
        when(reagentLotRepository.save(any(ReagentLot.class)))
            .thenThrow(new DataIntegrityViolationException("unique constraint"));

        assertThatThrownBy(() -> reagentService.createLot(defaultRequest("em_estoque")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Já existe um lote com este número e fabricante");
    }

    // ===== updateLot =====

    @Test
    @DisplayName("updateLot com status='inativo' rejeitado com 400")
    void updateLot_statusInativo_rejeitado() {
        ReagentLot lot = lot(50, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.updateLot(lot.getId(), defaultRequest("inativo")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Status 'inativo' nao pode ser definido em CREATE/UPDATE");
    }

    @Test
    @DisplayName("updateLot em lote ja inativo retorna 400 (forca uso de unarchive)")
    void updateLot_loteInativo_rejeitado() {
        ReagentLot lot = lot(0, 0);
        lot.setStatus(ReagentStatus.INATIVO);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.updateLot(lot.getId(), defaultRequest("em_estoque")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Lote arquivado nao pode ser editado diretamente");
    }

    @Test
    @DisplayName("updateLot mudando status para em_uso com openedDate=null grava audit BACKFILLED (v2 invariante)")
    void updateLot_emUso_backfillOpenedDate_emiteAuditBackfilled() {
        ReagentLot lot = lot(45, 5);
        lot.setStatus(ReagentStatus.EM_ESTOQUE);
        lot.setOpenedDate(null);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));

        // Request envia em_uso com unitsInUse=5 — backfill administrativo grava openedDate
        // pelo UPDATE path (audit ressalva 1.7 v2 preservado).
        ReagentLotRequest req = fullRequest(
            "ALT", "L123", "Bio", "Bioquímica",
            45, 5, "em_uso",
            LocalDate.now().plusDays(30),
            "Geladeira 2", "2-8°C",
            null, null, null
        );

        reagentService.updateLot(lot.getId(), req);

        assertThat(lot.getStatus()).isEqualTo(ReagentStatus.EM_USO);
        assertThat(lot.getOpenedDate()).isEqualTo(LocalDate.now());

        // BACKFILLED (v2 invariante preservado em v3 pelo path UPDATE).
        List<RecordingAuditService.Call> backfilled = auditService.callsFor(
            ReagentService.AUDIT_ACTION_OPENED_DATE_BACKFILLED);
        assertThat(backfilled).hasSize(1);
        assertThat(backfilled.getFirst().details())
            .containsEntry("trigger", ReagentService.AUDIT_TRIGGER_UPDATE_LOT)
            .containsEntry("toStatus", ReagentStatus.EM_USO);
    }

    @Test
    @DisplayName("updateLot com expiryDate passada forca vencido")
    void updateLot_setValidadePassada_deveVirarVencido() {
        ReagentLot lot = lot(25, 0);
        lot.setStatus(ReagentStatus.EM_ESTOQUE);
        lot.setExpiryDate(LocalDate.now().plusDays(30));
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));

        ReagentLotRequest req = fullRequest(
            "ALT", "L123", "Bio", "Bioquímica",
            25, 0, "em_estoque",
            LocalDate.now().minusDays(1),
            "Geladeira 2", "2-8°C",
            null, null, null
        );

        ReagentLot updated = reagentService.updateLot(lot.getId(), req);

        assertThat(updated.getStatus()).isEqualTo(ReagentStatus.VENCIDO);
    }

    // ===== createMovement =====

    @Test
    @DisplayName("ENTRADA aumenta unitsInStock")
    void entrada_aumentaUnitsInStock() {
        ReagentLot lot = lot(5, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        StockMovement mv = reagentService.createMovement(lot.getId(),
            new StockMovementRequest("ENTRADA", 10D, "Ana", "", null, null, null, null));

        assertThat(lot.getUnitsInStock()).isEqualTo(15);
        assertThat(lot.getUnitsInUse()).isEqualTo(0);
        assertThat(mv.getPreviousUnitsInStock()).isEqualTo(5);
        assertThat(mv.getPreviousUnitsInUse()).isEqualTo(0);
    }

    @Test
    @DisplayName("ABERTURA q=1 com unitsInStock=5 → 4 fechadas, 1 aberta, status=em_uso, openedDate=today")
    void abertura_q1_aberturaPrimeiraUnidade() {
        ReagentLot lot = lot(5, 0);
        lot.setStatus(ReagentStatus.EM_ESTOQUE);
        lot.setOpenedDate(null);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        reagentService.createMovement(lot.getId(),
            new StockMovementRequest("ABERTURA", 1D, "Ana", "", null, null, null, null));

        assertThat(lot.getUnitsInStock()).isEqualTo(4);
        assertThat(lot.getUnitsInUse()).isEqualTo(1);
        assertThat(lot.getStatus()).isEqualTo(ReagentStatus.EM_USO);
        assertThat(lot.getOpenedDate()).isEqualTo(LocalDate.now());

        // Audit DERIVED (v3 — ABERTURA path).
        List<RecordingAuditService.Call> derived = auditService.callsFor(
            ReagentService.AUDIT_ACTION_OPENED_DATE_DERIVED);
        assertThat(derived).hasSize(1);
        assertThat(derived.getFirst().details())
            .containsEntry("trigger", ReagentService.AUDIT_TRIGGER_ABERTURA);

        // BACKFILLED NAO emitido (path ABERTURA, nao UPDATE administrativo).
        assertThat(auditService.callsFor(ReagentService.AUDIT_ACTION_OPENED_DATE_BACKFILLED))
            .isEmpty();
    }

    @Test
    @DisplayName("ABERTURA com unitsInStock=0 → 400 (sem unidades fechadas)")
    void abertura_semEstoque_falha() {
        ReagentLot lot = lot(0, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.createMovement(lot.getId(),
            new StockMovementRequest("ABERTURA", 1D, "Ana", "", null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Sem unidades fechadas para abrir");
    }

    @Test
    @DisplayName("ABERTURA com quantity != 1 → 400 (audit ressalva E)")
    void abertura_quantityDiferente1_falha() {
        ReagentLot lot = lot(5, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.createMovement(lot.getId(),
            new StockMovementRequest("ABERTURA", 5D, "Ana", "", null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("operam unitariamente");
    }

    @Test
    @DisplayName("FECHAMENTO q=1 com unitsInUse=2 → 1 em uso, +1 em estoque, reason default REVERSAO_ABERTURA")
    void fechamento_q1_inverso_aberturaUnidade() {
        ReagentLot lot = lot(3, 2);
        lot.setStatus(ReagentStatus.EM_USO);
        lot.setOpenedDate(LocalDate.now().minusDays(2));
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        StockMovement mv = reagentService.createMovement(lot.getId(),
            new StockMovementRequest("FECHAMENTO", 1D, "Ana", "", null, null, null, null));

        assertThat(lot.getUnitsInStock()).isEqualTo(4);
        assertThat(lot.getUnitsInUse()).isEqualTo(1);
        assertThat(mv.getReason()).isEqualTo("REVERSAO_ABERTURA");
        // openedDate preservado — fechamento e reversao de engano.
        assertThat(lot.getOpenedDate()).isEqualTo(LocalDate.now().minusDays(2));
    }

    @Test
    @DisplayName("FECHAMENTO com unitsInUse=0 → 400")
    void fechamento_semUso_falha() {
        ReagentLot lot = lot(5, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.createMovement(lot.getId(),
            new StockMovementRequest("FECHAMENTO", 1D, "Ana", "", null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Sem unidades em uso");
    }

    @Test
    @DisplayName("FECHAMENTO com quantity != 1 → 400")
    void fechamento_quantityDiferente1_falha() {
        ReagentLot lot = lot(2, 3);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.createMovement(lot.getId(),
            new StockMovementRequest("FECHAMENTO", 2D, "Ana", "", null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("operam unitariamente");
    }

    @Test
    @DisplayName("CONSUMO q=2 com unitsInUse=2 → 0 em uso, status mantem em_uso (NAO arquiva)")
    void consumo_q2_zeraUso_mantemStatus() {
        ReagentLot lot = lot(0, 2);
        lot.setStatus(ReagentStatus.EM_USO);
        lot.setOpenedDate(LocalDate.now().minusDays(1));
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        reagentService.createMovement(lot.getId(),
            new StockMovementRequest("CONSUMO", 2D, "Ana", "", "OUTRO", null, null, null));

        assertThat(lot.getUnitsInUse()).isEqualTo(0);
        // Status nao reverte para terminal automatico (estoque zero deixou de ser terminal).
        // Mantem em_estoque (zero/zero) — NAO arquiva.
        assertThat(lot.getStatus()).isIn(ReagentStatus.EM_ESTOQUE, ReagentStatus.EM_USO);
    }

    @Test
    @DisplayName("CONSUMO em vencido sem reason → 400 (decisao orchestrator)")
    void consumo_emVencidoSemReason_falha() {
        ReagentLot lot = lot(0, 5);
        lot.setStatus(ReagentStatus.VENCIDO);
        lot.setExpiryDate(LocalDate.now().minusDays(3));
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.createMovement(lot.getId(),
            new StockMovementRequest("CONSUMO", 1D, "Ana", "", null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("CONSUMO em lote vencido exige reason");
    }

    @Test
    @DisplayName("CONSUMO em vencido com reason VENCIMENTO permitido (descarte)")
    void consumo_emVencidoComReason_permitido() {
        ReagentLot lot = lot(0, 5);
        lot.setStatus(ReagentStatus.VENCIDO);
        lot.setExpiryDate(LocalDate.now().minusDays(3));
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        reagentService.createMovement(lot.getId(),
            new StockMovementRequest("CONSUMO", 1D, "Ana", "", "VENCIMENTO", null, null, null));

        assertThat(lot.getUnitsInUse()).isEqualTo(4);
        assertThat(lot.getStatus()).isEqualTo(ReagentStatus.VENCIDO);
    }

    @Test
    @DisplayName("AJUSTE em inativo permitido com reason e targets (decisao 1.4)")
    void ajuste_emInativo_permitido() {
        ReagentLot lot = lot(0, 0);
        lot.setStatus(ReagentStatus.INATIVO);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        reagentService.createMovement(lot.getId(),
            new StockMovementRequest("AJUSTE", 0D, "Ana", "Recontagem", "CORRECAO", 5, 0, null));

        assertThat(lot.getUnitsInStock()).isEqualTo(5);
        assertThat(lot.getUnitsInUse()).isEqualTo(0);
        assertThat(lot.getStatus()).isEqualTo(ReagentStatus.INATIVO); // terminal manual preservado
        assertThat(lot.getNeedsStockReview()).isFalse();
    }

    @Test
    @DisplayName("AJUSTE limpa needsStockReview")
    void ajuste_limpaNeedsStockReview() {
        ReagentLot lot = lot(10, 0);
        lot.setNeedsStockReview(true);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        reagentService.createMovement(lot.getId(),
            new StockMovementRequest("AJUSTE", 0D, "Ana", "Recontagem", "CORRECAO", 5, 2, null));

        assertThat(lot.getNeedsStockReview()).isFalse();
    }

    @Test
    @DisplayName("AJUSTE sem reason → 400")
    void ajuste_semReason_falha() {
        ReagentLot lot = lot(50, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.createMovement(lot.getId(),
            new StockMovementRequest("AJUSTE", 0D, "Ana", "", null, 30, 0, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("AJUSTE exige reason");
    }

    @Test
    @DisplayName("AJUSTE sem targetUnitsInStock → 400")
    void ajuste_semTargetStock_falha() {
        ReagentLot lot = lot(50, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.createMovement(lot.getId(),
            new StockMovementRequest("AJUSTE", 0D, "Ana", "", "CORRECAO", null, 0, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("AJUSTE exige targetUnitsInStock e targetUnitsInUse");
    }

    @Test
    @DisplayName("ENTRADA em inativo bloqueada com 400 + audit")
    void entrada_emInativo_bloqueia() {
        ReagentLot lot = lot(0, 0);
        lot.setStatus(ReagentStatus.INATIVO);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.createMovement(lot.getId(),
            new StockMovementRequest("ENTRADA", 5D, "Ana", "", null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("nao aceita ENTRADA");

        List<RecordingAuditService.Call> blocked = auditService.callsFor(
            ReagentService.AUDIT_ACTION_MOVEMENT_BLOCKED);
        assertThat(blocked).hasSize(1);
        assertThat(blocked.getFirst().details())
            .containsEntry("reason", "lote_inativo")
            .containsEntry("movementType", "ENTRADA");
    }

    @Test
    @DisplayName("ENTRADA em vencido bloqueada com 400 + audit")
    void entrada_emVencido_bloqueia() {
        ReagentLot lot = lot(5, 0);
        lot.setStatus(ReagentStatus.VENCIDO);
        lot.setExpiryDate(LocalDate.now().minusDays(5));
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.createMovement(lot.getId(),
            new StockMovementRequest("ENTRADA", 10D, "Ana", "", null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("nao aceita ENTRADA");

        List<RecordingAuditService.Call> blocked = auditService.callsFor(
            ReagentService.AUDIT_ACTION_MOVEMENT_BLOCKED);
        assertThat(blocked).hasSize(1);
        assertThat(blocked.getFirst().details())
            .containsEntry("reason", "lote_vencido");
    }

    @Test
    @DisplayName("ABERTURA em inativo bloqueada")
    void abertura_emInativo_bloqueia() {
        ReagentLot lot = lot(5, 0);
        lot.setStatus(ReagentStatus.INATIVO);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.createMovement(lot.getId(),
            new StockMovementRequest("ABERTURA", 1D, "Ana", "", null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("nao aceita ABERTURA");
    }

    @Test
    @DisplayName("FECHAMENTO em vencido bloqueada")
    void fechamento_emVencido_bloqueia() {
        ReagentLot lot = lot(2, 1);
        lot.setStatus(ReagentStatus.VENCIDO);
        lot.setExpiryDate(LocalDate.now().minusDays(3));
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.createMovement(lot.getId(),
            new StockMovementRequest("FECHAMENTO", 1D, "Ana", "", null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("nao aceita FECHAMENTO");
    }

    @Test
    @DisplayName("SAIDA em createMovement → 400 (descontinuado em v3)")
    void saida_emCreateMovement_recusada() {
        ReagentLot lot = lot(20, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.createMovement(lot.getId(),
            new StockMovementRequest("SAIDA", 5D, "Ana", "", null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("SAIDA descontinuado");
    }

    // ===== v3.1 — eventDate (data declarada pelo operador) =====

    @Test
    @DisplayName("ABERTURA com eventDate=2026-04-01 (passada) e openedDate=null grava lot.openedDate=2026-04-01 + movement.eventDate=2026-04-01 + audit.openedDate='2026-04-01'")
    void abertura_eventDatePassada_sincronizaOpenedDateEMovimento() {
        ReagentLot lot = lot(5, 0);
        lot.setStatus(ReagentStatus.EM_ESTOQUE);
        lot.setOpenedDate(null);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        LocalDate declared = LocalDate.of(2026, 4, 1);
        StockMovement mv = reagentService.createMovement(lot.getId(),
            new StockMovementRequest("ABERTURA", 1D, "Ana", "", null, null, null, declared));

        assertThat(lot.getOpenedDate()).isEqualTo(declared);
        assertThat(mv.getEventDate()).isEqualTo(declared);

        List<RecordingAuditService.Call> derived = auditService.callsFor(
            ReagentService.AUDIT_ACTION_OPENED_DATE_DERIVED);
        assertThat(derived).hasSize(1);
        assertThat(derived.getFirst().details())
            .containsEntry("openedDate", declared.toString())
            .containsEntry("trigger", ReagentService.AUDIT_TRIGGER_ABERTURA);
    }

    @Test
    @DisplayName("ABERTURA sem eventDate (null) mantem comportamento v3: lot.openedDate=today, movement.eventDate=today")
    void abertura_semEventDate_defaultToday() {
        ReagentLot lot = lot(5, 0);
        lot.setStatus(ReagentStatus.EM_ESTOQUE);
        lot.setOpenedDate(null);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        StockMovement mv = reagentService.createMovement(lot.getId(),
            new StockMovementRequest("ABERTURA", 1D, "Ana", "", null, null, null, null));

        assertThat(lot.getOpenedDate()).isEqualTo(LocalDate.now());
        assertThat(mv.getEventDate()).isEqualTo(LocalDate.now());

        List<RecordingAuditService.Call> derived = auditService.callsFor(
            ReagentService.AUDIT_ACTION_OPENED_DATE_DERIVED);
        assertThat(derived).hasSize(1);
        assertThat(derived.getFirst().details())
            .containsEntry("openedDate", LocalDate.now().toString());
    }

    @Test
    @DisplayName("ABERTURA com eventDate futura → 400 BusinessException")
    void abertura_eventDateFutura_falha() {
        ReagentLot lot = lot(5, 0);
        lot.setStatus(ReagentStatus.EM_ESTOQUE);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        LocalDate futura = LocalDate.now().plusDays(1);
        assertThatThrownBy(() -> reagentService.createMovement(lot.getId(),
            new StockMovementRequest("ABERTURA", 1D, "Ana", "", null, null, null, futura)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Data de abertura não pode ser futura");
    }

    @Test
    @DisplayName("ABERTURA quando lot.openedDate ja existe NAO sobrescreve, mas movement.eventDate eh gravada")
    void abertura_openedDateJaExiste_naoSobrescreveMasGravaEventDate() {
        ReagentLot lot = lot(5, 1);
        lot.setStatus(ReagentStatus.EM_USO);
        LocalDate primeiraAbertura = LocalDate.now().minusDays(10);
        lot.setOpenedDate(primeiraAbertura);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        LocalDate segundaAbertura = LocalDate.now().minusDays(2);
        StockMovement mv = reagentService.createMovement(lot.getId(),
            new StockMovementRequest("ABERTURA", 1D, "Ana", "", null, null, null, segundaAbertura));

        // openedDate preservado — primeira abertura permanece imutavel.
        assertThat(lot.getOpenedDate()).isEqualTo(primeiraAbertura);
        // movement.eventDate carrega a data declarada da segunda abertura.
        assertThat(mv.getEventDate()).isEqualTo(segundaAbertura);

        // Audit DERIVED NAO emitido — lot.openedDate ja existia.
        assertThat(auditService.callsFor(ReagentService.AUDIT_ACTION_OPENED_DATE_DERIVED))
            .isEmpty();
    }

    @Test
    @DisplayName("CONSUMO com eventDate=2026-04-15 grava movement.eventDate sem mexer em lot.openedDate")
    void consumo_eventDatePassada_persisteSemAfetarLot() {
        ReagentLot lot = lot(0, 5);
        lot.setStatus(ReagentStatus.EM_USO);
        LocalDate openedOriginal = LocalDate.now().minusDays(20);
        lot.setOpenedDate(openedOriginal);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        LocalDate fimUso = LocalDate.of(2026, 4, 15);
        StockMovement mv = reagentService.createMovement(lot.getId(),
            new StockMovementRequest("CONSUMO", 1D, "Ana", "", null, null, null, fimUso));

        assertThat(mv.getEventDate()).isEqualTo(fimUso);
        // openedDate NAO eh tocado por CONSUMO.
        assertThat(lot.getOpenedDate()).isEqualTo(openedOriginal);
    }

    @Test
    @DisplayName("CONSUMO com eventDate futura → 400 BusinessException")
    void consumo_eventDateFutura_falha() {
        ReagentLot lot = lot(0, 5);
        lot.setStatus(ReagentStatus.EM_USO);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        LocalDate futura = LocalDate.now().plusDays(2);
        assertThatThrownBy(() -> reagentService.createMovement(lot.getId(),
            new StockMovementRequest("CONSUMO", 1D, "Ana", "", null, null, null, futura)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Data de fim de uso não pode ser futura");
    }

    @Test
    @DisplayName("CONSUMO sem eventDate → movement.eventDate=null (compat com UI antigo)")
    void consumo_semEventDate_persisteNull() {
        ReagentLot lot = lot(0, 5);
        lot.setStatus(ReagentStatus.EM_USO);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        StockMovement mv = reagentService.createMovement(lot.getId(),
            new StockMovementRequest("CONSUMO", 1D, "Ana", "", null, null, null, null));

        assertThat(mv.getEventDate()).isNull();
    }

    @Test
    @DisplayName("FECHAMENTO com eventDate persiste sem efeito colateral")
    void fechamento_comEventDate_persisteSemEfeito() {
        ReagentLot lot = lot(2, 1);
        lot.setStatus(ReagentStatus.EM_USO);
        LocalDate openedOriginal = LocalDate.now().minusDays(5);
        lot.setOpenedDate(openedOriginal);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        LocalDate eventDate = LocalDate.now().minusDays(1);
        StockMovement mv = reagentService.createMovement(lot.getId(),
            new StockMovementRequest("FECHAMENTO", 1D, "Ana", "", null, null, null, eventDate));

        assertThat(mv.getEventDate()).isEqualTo(eventDate);
        // openedDate NAO alterado por FECHAMENTO.
        assertThat(lot.getOpenedDate()).isEqualTo(openedOriginal);
    }

    @Test
    @DisplayName("ENTRADA com eventDate persiste sem efeito colateral")
    void entrada_comEventDate_persisteSemEfeito() {
        ReagentLot lot = lot(5, 0);
        lot.setStatus(ReagentStatus.EM_ESTOQUE);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));

        LocalDate eventDate = LocalDate.now().minusDays(3);
        StockMovement mv = reagentService.createMovement(lot.getId(),
            new StockMovementRequest("ENTRADA", 10D, "Ana", "", null, null, null, eventDate));

        assertThat(mv.getEventDate()).isEqualTo(eventDate);
        assertThat(lot.getUnitsInStock()).isEqualTo(15);
    }

    // ===== archive =====

    @Test
    @DisplayName("archive happy path: status=inativo, archivedAt/By set, audit")
    void archive_happy() {
        ReagentLot lot = lot(5, 0);
        lot.setStatus(ReagentStatus.EM_ESTOQUE);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.existsActiveResponsibleByUsername(eq("ana"), any()))
            .thenReturn(true);

        ReagentLot result = reagentService.archiveLot(lot.getId(),
            new ArchiveReagentLotRequest(LocalDate.now(), "ana"));

        assertThat(result.getStatus()).isEqualTo(ReagentStatus.INATIVO);
        assertThat(result.getArchivedAt()).isEqualTo(LocalDate.now());
        assertThat(result.getArchivedBy()).isEqualTo("ana");
        assertThat(result.getNeedsStockReview()).isFalse();

        List<RecordingAuditService.Call> archived = auditService.callsFor(
            ReagentService.AUDIT_ACTION_LOT_ARCHIVED);
        assertThat(archived).hasSize(1);
        assertThat(archived.getFirst().details())
            .containsEntry("toStatus", ReagentStatus.INATIVO)
            .containsEntry("archivedBy", "ana");
    }

    @Test
    @DisplayName("archive com archivedBy username inexistente → 400")
    void archive_archivedByInvalido_falha() {
        ReagentLot lot = lot(5, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(userRepository.existsActiveResponsibleByUsername(eq("fantasma"), any()))
            .thenReturn(false);

        assertThatThrownBy(() -> reagentService.archiveLot(lot.getId(),
            new ArchiveReagentLotRequest(LocalDate.now(), "fantasma")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("nao encontrado ou inativo");
    }

    @Test
    @DisplayName("archive com archivedAt futura → 400")
    void archive_archivedAtFutura_falha() {
        ReagentLot lot = lot(5, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.archiveLot(lot.getId(),
            new ArchiveReagentLotRequest(LocalDate.now().plusDays(1), "ana")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("nao pode ser data futura");
    }

    @Test
    @DisplayName("archive em lote ja inativo → 400")
    void archive_jaInativo_falha() {
        ReagentLot lot = lot(0, 0);
        lot.setStatus(ReagentStatus.INATIVO);
        lot.setArchivedAt(LocalDate.now().minusDays(2));
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.archiveLot(lot.getId(),
            new ArchiveReagentLotRequest(LocalDate.now(), "ana")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("ja arquivado");
    }

    @Test
    @DisplayName("archive valida archivedBy por USERNAME (audit ressalva 1.1) — name colidindo nao engana")
    void archive_buscaPorUsernameNaoName() {
        // Cenario: dois usuarios com mesmo `name='Joao Silva'` mas usernames distintos.
        // Repository so encontra o que bate por USERNAME — chave estavel.
        ReagentLot lot = lot(5, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.existsActiveResponsibleByUsername(eq("jsilva2"), any()))
            .thenReturn(true);
        // jsilva (outro usuario com mesmo name) nao foi consultado — irrelevante.

        ReagentLot result = reagentService.archiveLot(lot.getId(),
            new ArchiveReagentLotRequest(LocalDate.now(), "jsilva2"));

        assertThat(result.getArchivedBy()).isEqualTo("jsilva2");
    }

    // ===== unarchive =====

    @Test
    @DisplayName("unarchive de inativo + expiry passou → vencido")
    void unarchive_expiryPassada_viraVencido() {
        ReagentLot lot = lot(2, 0);
        lot.setStatus(ReagentStatus.INATIVO);
        lot.setExpiryDate(LocalDate.now().minusDays(3));
        lot.setArchivedAt(LocalDate.now().minusDays(10));
        lot.setArchivedBy("ana");
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));

        ReagentLot result = reagentService.unarchiveLot(lot.getId(),
            new UnarchiveReagentLotRequest("auditor pediu"));

        assertThat(result.getStatus()).isEqualTo(ReagentStatus.VENCIDO);
        // Preserva archivedAt/By.
        assertThat(result.getArchivedAt()).isEqualTo(LocalDate.now().minusDays(10));
        assertThat(result.getArchivedBy()).isEqualTo("ana");
    }

    @Test
    @DisplayName("unarchive de inativo + expiry futura + unitsInStock>0 + unitsInUse=0 → em_estoque")
    void unarchive_emEstoque() {
        ReagentLot lot = lot(5, 0);
        lot.setStatus(ReagentStatus.INATIVO);
        lot.setExpiryDate(LocalDate.now().plusDays(60));
        lot.setArchivedAt(LocalDate.now().minusDays(5));
        lot.setArchivedBy("ana");
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));

        ReagentLot result = reagentService.unarchiveLot(lot.getId(),
            new UnarchiveReagentLotRequest(null));

        assertThat(result.getStatus()).isEqualTo(ReagentStatus.EM_ESTOQUE);
        assertThat(result.getArchivedAt()).isEqualTo(LocalDate.now().minusDays(5));
    }

    @Test
    @DisplayName("unarchive de inativo + unitsInUse>0 → em_uso")
    void unarchive_emUso() {
        ReagentLot lot = lot(2, 1);
        lot.setStatus(ReagentStatus.INATIVO);
        lot.setExpiryDate(LocalDate.now().plusDays(60));
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));

        ReagentLot result = reagentService.unarchiveLot(lot.getId(),
            new UnarchiveReagentLotRequest(null));

        assertThat(result.getStatus()).isEqualTo(ReagentStatus.EM_USO);
    }

    @Test
    @DisplayName("unarchive de inativo zero/zero → em_estoque (NAO volta para inativo)")
    void unarchive_zeroZero_viraEmEstoque() {
        ReagentLot lot = lot(0, 0);
        lot.setStatus(ReagentStatus.INATIVO);
        lot.setExpiryDate(LocalDate.now().plusDays(60));
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));

        ReagentLot result = reagentService.unarchiveLot(lot.getId(),
            new UnarchiveReagentLotRequest(null));

        assertThat(result.getStatus()).isEqualTo(ReagentStatus.EM_ESTOQUE);
    }

    @Test
    @DisplayName("unarchive de lote nao-inativo → 400")
    void unarchive_naoInativo_falha() {
        ReagentLot lot = lot(5, 0);
        lot.setStatus(ReagentStatus.EM_ESTOQUE);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.unarchiveLot(lot.getId(),
            new UnarchiveReagentLotRequest(null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Lote nao esta arquivado");
    }

    @Test
    @DisplayName("unarchive emite audit REAGENT_LOT_UNARCHIVED com archivedAt/By preservados")
    void unarchive_audit() {
        ReagentLot lot = lot(5, 0);
        lot.setStatus(ReagentStatus.INATIVO);
        lot.setArchivedAt(LocalDate.now().minusDays(7));
        lot.setArchivedBy("ana");
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));

        reagentService.unarchiveLot(lot.getId(), new UnarchiveReagentLotRequest("teste"));

        List<RecordingAuditService.Call> calls = auditService.callsFor(
            ReagentService.AUDIT_ACTION_LOT_UNARCHIVED);
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().details())
            .containsEntry("archivedAtPreserved", LocalDate.now().minusDays(7).toString())
            .containsEntry("archivedByPreserved", "ana")
            .containsEntry("reason", "teste");
    }

    // ===== deleteLot =====

    @Test
    @DisplayName("deleteLot com confirmLotNumber mismatch → 400")
    void deleteLot_confirmMismatch_falha() {
        ReagentLot lot = lot(0, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> reagentService.deleteLot(lot.getId(),
            new DeleteReagentLotRequest("OUTRO")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Confirmacao do lote nao confere");

        verify(reagentLotRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteLot com usedInQcRecently=true → 400 + audit DELETE_BLOCKED, nada apagado")
    void deleteLot_usedInQc_bloqueia() {
        ReagentLot lot = lot(0, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(qcRecordRepository.existsByLotNumberOperational(lot.getLotNumber())).thenReturn(true);

        assertThatThrownBy(() -> reagentService.deleteLot(lot.getId(),
            new DeleteReagentLotRequest("L123")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("CQ recente");

        verify(reagentLotRepository, never()).deleteById(any());
        List<RecordingAuditService.Call> blocked = auditService.callsFor(
            ReagentService.AUDIT_ACTION_DELETE_BLOCKED);
        assertThat(blocked).hasSize(1);
        assertThat(blocked.getFirst().details())
            .containsEntry("reason", "used_in_qc_recently");
    }

    @Test
    @DisplayName("deleteLot happy path: audit REAGENT_LOT_DELETED com snapshot enumerativo de movements")
    void deleteLot_happy_snapshotEnumerativo() {
        ReagentLot lot = lot(0, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(qcRecordRepository.existsByLotNumberOperational(lot.getLotNumber())).thenReturn(false);

        // Lista de movements para snapshot.
        StockMovement m1 = StockMovement.builder()
            .id(UUID.randomUUID())
            .reagentLot(lot).type("ENTRADA").quantity(10D)
            .responsible("Ana").build();
        StockMovement m2 = StockMovement.builder()
            .id(UUID.randomUUID())
            .reagentLot(lot).type("ABERTURA").quantity(1D)
            .responsible("Ana").build();
        when(stockMovementRepository.findByReagentLotIdOrderByCreatedAtDesc(lot.getId()))
            .thenReturn(List.of(m1, m2));

        reagentService.deleteLot(lot.getId(), new DeleteReagentLotRequest("L123"));

        verify(reagentLotRepository).deleteById(lot.getId());

        List<RecordingAuditService.Call> deleted = auditService.callsFor(
            ReagentService.AUDIT_ACTION_LOT_DELETED);
        assertThat(deleted).hasSize(1);
        // Snapshot deve incluir movements como array nao-vazio (audit ressalva 1.2).
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> movements = (List<Map<String, Object>>) deleted.getFirst().details().get("movements");
        assertThat(movements).hasSize(2);
        assertThat(movements.get(0)).containsKeys("id", "type", "quantity", "responsible", "createdAt");
        assertThat(deleted.getFirst().details()).containsEntry("movementsCount", 2L);
    }

    @Test
    @DisplayName("deleteLot sem movements: snapshot tem array vazio mas existe")
    void deleteLot_semMovements_snapshotVazio() {
        ReagentLot lot = lot(0, 0);
        when(reagentLotRepository.findById(lot.getId())).thenReturn(Optional.of(lot));
        when(qcRecordRepository.existsByLotNumberOperational(lot.getLotNumber())).thenReturn(false);
        when(stockMovementRepository.findByReagentLotIdOrderByCreatedAtDesc(lot.getId()))
            .thenReturn(List.of());

        reagentService.deleteLot(lot.getId(), new DeleteReagentLotRequest("L123"));

        verify(reagentLotRepository).deleteById(lot.getId());
        List<RecordingAuditService.Call> deleted = auditService.callsFor(
            ReagentService.AUDIT_ACTION_LOT_DELETED);
        assertThat(deleted).hasSize(1);
    }

    // ===== deriveStatus =====

    @Test
    @DisplayName("deriveStatus: inativo respeita terminal manual")
    void deriveStatus_inativoRespeita() {
        ReagentLot lot = lot(5, 0);
        lot.setStatus(ReagentStatus.INATIVO);
        assertThat(reagentService.deriveStatus(lot, LocalDate.now()))
            .isEqualTo(ReagentStatus.INATIVO);
    }

    @Test
    @DisplayName("deriveStatus: expiry < hoje retorna vencido (mais forte)")
    void deriveStatus_expiryPassada_vencido() {
        ReagentLot lot = lot(5, 0);
        lot.setExpiryDate(LocalDate.now().minusDays(1));
        assertThat(reagentService.deriveStatus(lot, LocalDate.now()))
            .isEqualTo(ReagentStatus.VENCIDO);
    }

    @Test
    @DisplayName("deriveStatus: unitsInUse > 0 retorna em_uso")
    void deriveStatus_unitsInUse_emUso() {
        ReagentLot lot = lot(2, 3);
        assertThat(reagentService.deriveStatus(lot, LocalDate.now()))
            .isEqualTo(ReagentStatus.EM_USO);
    }

    @Test
    @DisplayName("deriveStatus: unitsInStock > 0 e unitsInUse == 0 → em_estoque")
    void deriveStatus_emEstoque() {
        ReagentLot lot = lot(5, 0);
        assertThat(reagentService.deriveStatus(lot, LocalDate.now()))
            .isEqualTo(ReagentStatus.EM_ESTOQUE);
    }

    @Test
    @DisplayName("deriveStatus: zero/zero mantem status atual (NAO terminal automatico)")
    void deriveStatus_zeroZero_mantemStatus() {
        ReagentLot lot = lot(0, 0);
        lot.setStatus(ReagentStatus.EM_ESTOQUE);
        assertThat(reagentService.deriveStatus(lot, LocalDate.now()))
            .isEqualTo(ReagentStatus.EM_ESTOQUE);
    }

    // ===== getResponsibles =====

    @Test
    @DisplayName("getResponsibles retorna shape minimo filtrado")
    void getResponsibles_shapeMinimo() {
        User u1 = User.builder()
            .id(UUID.randomUUID())
            .username("ana")
            .name("Ana Silva")
            .email("ana@bio.com")
            .role(Role.FUNCIONARIO)
            .isActive(true)
            .build();
        when(userRepository.findActiveResponsibles(any())).thenReturn(List.of(u1));

        var responsibles = reagentService.getResponsibles();

        assertThat(responsibles).hasSize(1);
        assertThat(responsibles.getFirst().username()).isEqualTo("ana");
        assertThat(responsibles.getFirst().name()).isEqualTo("Ana Silva");
        assertThat(responsibles.getFirst().role()).isEqualTo("FUNCIONARIO");
    }

    // ===== getLots =====

    @Test
    @DisplayName("getLots: filtro por status inativo permitido (v3)")
    void getLots_filtraInativo() {
        ReagentLot l = lot(0, 0);
        l.setStatus(ReagentStatus.INATIVO);
        when(reagentLotRepository.findByFilters(isNull(), eq("inativo")))
            .thenReturn(List.of(l));
        when(qcRecordRepository.findActiveLotNumbersSince(any(), any())).thenReturn(Collections.emptyList());

        var result = reagentService.getLots(null, "inativo");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isEqualTo("inativo");
    }

    @Test
    @DisplayName("getLots: filtro por status legado fora_de_estoque retorna 400")
    void getLots_statusLegado_falha() {
        assertThatThrownBy(() -> reagentService.getLots(null, "fora_de_estoque"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Status legado nao suportado");
    }

    @Test
    @DisplayName("getLots expoe canReceiveEntry=false para vencido E inativo")
    void getLots_canReceiveEntry() {
        ReagentLot vencido = lot(0, 0);
        vencido.setLotNumber("L-V");
        vencido.setStatus(ReagentStatus.VENCIDO);
        ReagentLot inativo = lot(0, 0);
        inativo.setLotNumber("L-I");
        inativo.setStatus(ReagentStatus.INATIVO);

        when(reagentLotRepository.findByFilters(isNull(), isNull()))
            .thenReturn(List.of(vencido, inativo));
        when(qcRecordRepository.findActiveLotNumbersSince(any(), any())).thenReturn(Collections.emptyList());

        var result = reagentService.getLots(null, null);

        assertThat(result.get(0).canReceiveEntry()).isFalse();
        assertThat(result.get(0).allowedMovementTypes()).containsExactly("CONSUMO", "AJUSTE");
        assertThat(result.get(1).canReceiveEntry()).isFalse();
        assertThat(result.get(1).allowedMovementTypes()).containsExactly("AJUSTE");
    }

    // ===== Categorias / temperaturas (mantidos do v2) =====

    @org.junit.jupiter.params.ParameterizedTest(name = "createLot aceita categoria canonica: {0}")
    @org.junit.jupiter.params.provider.MethodSource(
        "com.bioqc.service.ReagentServiceTest#allowedCategoriesProvider")
    @DisplayName("ALLOWED_CATEGORIES: cada categoria canonica e aceita por createLot (G-01)")
    void allCategorias_canonicas_saoAceitas(String category) {
        when(reagentLotRepository.save(any(ReagentLot.class))).thenAnswer(i -> i.getArgument(0));

        ReagentLotRequest req = fullRequest(
            "ALT", "L-CAT-" + Math.abs(category.hashCode()), "Bio", category,
            8, 0, "em_estoque",
            LocalDate.now().plusDays(60),
            "Geladeira 2", "2-8°C",
            null, null, null
        );

        ReagentLot lot = reagentService.createLot(req);
        assertThat(lot.getCategory()).isEqualTo(category);
    }

    @Test
    @DisplayName("ALLOWED_CATEGORIES espelha frontend constants.ts (G-01)")
    void categorias_espelhamFrontendConstantsTs() {
        assertThat(ReagentService.ALLOWED_CATEGORIES).containsExactly(
            "Bioquímica", "Hematologia", "Imunologia", "Parasitologia",
            "Microbiologia", "Uroanálise", "Kit Diagnóstico",
            "Controle CQ", "Calibrador", "Geral"
        );
    }

    @Test
    @DisplayName("ALLOWED_STORAGE_TEMPS espelha frontend constants.ts (G-02)")
    void temperaturas_espelhamFrontendConstantsTs() {
        assertThat(ReagentService.ALLOWED_STORAGE_TEMPS).containsExactly(
            "2-8°C", "15-25°C (Ambiente)", "-20°C", "-80°C"
        );
    }

    static java.util.stream.Stream<String> allowedCategoriesProvider() {
        return ReagentService.ALLOWED_CATEGORIES.stream();
    }
}
