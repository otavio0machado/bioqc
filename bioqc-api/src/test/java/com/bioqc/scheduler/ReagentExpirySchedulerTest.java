package com.bioqc.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bioqc.entity.ReagentLot;
import com.bioqc.entity.ReagentStatus;
import com.bioqc.repository.QcRecordRepository;
import com.bioqc.repository.ReagentLotRepository;
import com.bioqc.repository.StockMovementRepository;
import com.bioqc.repository.UserRepository;
import com.bioqc.service.AuditService;
import com.bioqc.service.ReagentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Testa {@link ReagentExpiryScheduler#markExpiredLots()} apos refator-v3.
 *
 * <p>Cobre os seguintes cenarios canonicos:</p>
 * <ul>
 *   <li>Lote em_estoque + validade passada → vencido + audit trigger=scheduler</li>
 *   <li>Lote inativo + validade passada → scheduler NAO toca (terminal manual)</li>
 *   <li>Lote em_estoque + validade futura → no-op</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReagentExpirySchedulerTest {

    @Mock
    private ReagentLotRepository reagentLotRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private QcRecordRepository qcRecordRepository;

    @Mock
    private UserRepository userRepository;

    private RecordingAuditService auditService;
    private ReagentService reagentService;
    private ReagentExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        auditService = new RecordingAuditService();
        reagentService = new ReagentService(
            reagentLotRepository, stockMovementRepository, qcRecordRepository,
            userRepository, auditService);
        scheduler = new ReagentExpiryScheduler(reagentLotRepository, reagentService);
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

    @Test
    @DisplayName("lote em_estoque + validade passada vira vencido")
    void emEstoque_comValidadePassada_deveVirarVencido() {
        ReagentLot lot = lotBuilder(ReagentStatus.EM_ESTOQUE, LocalDate.now().minusDays(3), 15, 0);
        when(reagentLotRepository.findExpiredNeedingReclassification(any()))
            .thenReturn(List.of(lot));

        scheduler.markExpiredLots();

        assertThat(lot.getStatus()).isEqualTo(ReagentStatus.VENCIDO);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReagentLot>> captor = ArgumentCaptor.forClass(List.class);
        verify(reagentLotRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(lot);
    }

    @Test
    @DisplayName("lote em_uso + validade passada vira vencido")
    void emUso_comValidadePassada_deveVirarVencido() {
        ReagentLot lot = lotBuilder(ReagentStatus.EM_USO, LocalDate.now().minusDays(3), 0, 5);
        when(reagentLotRepository.findExpiredNeedingReclassification(any()))
            .thenReturn(List.of(lot));

        scheduler.markExpiredLots();

        assertThat(lot.getStatus()).isEqualTo(ReagentStatus.VENCIDO);
    }

    @Test
    @DisplayName("scheduler NAO toca lote inativo (terminal manual — decisao 1.1)")
    void inativo_comValidadePassada_naoTocado() {
        // O repository ja filtra inativo via JPQL (status NOT IN ('vencido','inativo')),
        // mas testamos defesa em profundidade: mesmo se chegasse aqui, applyDerivedStatusFromScheduler
        // faz early return em lote inativo.
        ReagentLot lot = lotBuilder(ReagentStatus.INATIVO, LocalDate.now().minusDays(10), 0, 0);
        when(reagentLotRepository.findExpiredNeedingReclassification(any()))
            .thenReturn(List.of(lot));

        scheduler.markExpiredLots();

        // Status NAO mudou (terminal manual preservado).
        assertThat(lot.getStatus()).isEqualTo(ReagentStatus.INATIVO);
        verify(reagentLotRepository, never()).saveAll(any());
        // Sem audit DERIVED.
        assertThat(auditService.callsFor(ReagentService.AUDIT_ACTION_STATUS_DERIVED)).isEmpty();
    }

    @Test
    @DisplayName("candidatos vazios nao geram saveAll")
    void semCandidatos_naoChamaSaveAll() {
        when(reagentLotRepository.findExpiredNeedingReclassification(any()))
            .thenReturn(List.of());

        scheduler.markExpiredLots();

        verify(reagentLotRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("scheduler grava audit log REAGENT_STATUS_DERIVED trigger=scheduler para cada reclassificacao")
    void scheduler_gravaAuditLogPorLoteReclassificado() {
        ReagentLot l1 = lotBuilder(ReagentStatus.EM_ESTOQUE, LocalDate.now().minusDays(2), 10, 0);
        ReagentLot l2 = lotBuilder(ReagentStatus.EM_ESTOQUE, LocalDate.now().minusDays(2), 0, 0);
        when(reagentLotRepository.findExpiredNeedingReclassification(any()))
            .thenReturn(List.of(l1, l2));

        scheduler.markExpiredLots();

        List<RecordingAuditService.Call> derived = auditService.callsFor(
            ReagentService.AUDIT_ACTION_STATUS_DERIVED);
        assertThat(derived).hasSize(2);
        assertThat(derived).allSatisfy(call -> {
            assertThat(call.entityType()).isEqualTo("ReagentLot");
            assertThat(call.details())
                .containsEntry("trigger", ReagentService.AUDIT_TRIGGER_SCHEDULER)
                .containsEntry("from", ReagentStatus.EM_ESTOQUE)
                .containsEntry("to", ReagentStatus.VENCIDO);
        });
    }

    @Test
    @DisplayName("scheduler nao grava audit quando nao ha transicao")
    void scheduler_semTransicao_naoGeraAudit() {
        ReagentLot lot = lotBuilder(ReagentStatus.VENCIDO, LocalDate.now().minusDays(3), 5, 0);
        when(reagentLotRepository.findExpiredNeedingReclassification(any()))
            .thenReturn(List.of(lot));

        scheduler.markExpiredLots();

        assertThat(auditService.callsFor(ReagentService.AUDIT_ACTION_STATUS_DERIVED)).isEmpty();
    }

    private ReagentLot lotBuilder(String status, LocalDate expiryDate, int stock, int use) {
        return ReagentLot.builder()
            .id(UUID.randomUUID())
            .name("ALT")
            .lotNumber("L-" + UUID.randomUUID())
            .manufacturer("Bio")
            .unitsInStock(stock)
            .unitsInUse(use)
            .status(status)
            .expiryDate(expiryDate)
            .needsStockReview(false)
            .build();
    }
}
