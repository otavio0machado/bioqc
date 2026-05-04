package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bioqc.dto.request.QcRecordRequest;
import com.bioqc.dto.response.BatchImportResult;
import com.bioqc.dto.response.ImportRunResponse;
import com.bioqc.dto.response.QcRecordResponse;
import com.bioqc.entity.ImportRun;
import com.bioqc.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class QcBatchImportServiceTest {

    private FakeQcService qcService;
    private FakeImportRunService importRunService;
    private FakeTransactionManager transactionManager;
    private QcBatchImportService service;

    @BeforeEach
    void setUp() {
        qcService = new FakeQcService();
        importRunService = new FakeImportRunService();
        transactionManager = new FakeTransactionManager();
        service = new QcBatchImportService(qcService, importRunService, transactionManager);
    }

    @Test
    @DisplayName("importPartial: valida e persiste linhas boas, registra falhas sem abortar")
    void importPartialSegreaSucessoEFalha() {
        QcRecordRequest r1 = request("Glicose");
        QcRecordRequest r2 = request("Sodio");
        QcRecordRequest r3 = request("Falha");
        qcService.responses.put(r1, response(r1));
        qcService.responses.put(r2, response(r2));
        qcService.errors.put(r3, new BusinessException("valor alvo invalido"));

        BatchImportResult result = service.importPartial(List.of(r1, r2, r3), null);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.results()).hasSize(3);
        assertThat(result.results().get(2).success()).isFalse();
        assertThat(result.results().get(2).message()).contains("valor alvo");
        assertThat(importRunService.lastMode).isEqualTo("PARTIAL");
        assertThat(importRunService.lastSuccess).isEqualTo(2);
        assertThat(importRunService.lastFailure).isEqualTo(1);
    }

    @Test
    @DisplayName("importPartial: lista vazia lanca BusinessException")
    void importPartialVazio() {
        assertThatThrownBy(() -> service.importPartial(List.of(), null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Nenhum registro");
    }

    @Test
    @DisplayName("importPartial: lista maior que 1000 lanca BusinessException")
    void importPartialAcimaLimite() {
        List<QcRecordRequest> big = new ArrayList<>();
        for (int i = 0; i < 1_001; i++) big.add(request("Glicose"));

        assertThatThrownBy(() -> service.importPartial(big, null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("1000");
    }

    @Test
    @DisplayName("importAtomic: delega para QcService e grava ImportRun")
    void importAtomicDelegaEGravaRun() {
        QcRecordRequest r1 = request("Glicose");
        qcService.atomicResponse = List.of(response(r1));

        BatchImportResult result = service.importAtomic(List.of(r1), null);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
        assertThat(result.mode()).isEqualTo("ATOMIC");
        assertThat(importRunService.lastMode).isEqualTo("ATOMIC");
    }

    // ---- helpers ----
    private QcRecordRequest request(String examName) {
        return new QcRecordRequest(examName, "bioquimica", LocalDate.now(), "Normal",
            "L1", 100.0, 100.0, 1.0, 10.0, "Eq", "Ana", null);
    }

    private QcRecordResponse response(QcRecordRequest r) {
        return new QcRecordResponse(
            UUID.randomUUID(), null, r.examName(), r.area(), r.date(), r.level(),
            r.lotNumber(), r.value(), r.targetValue(), r.targetSd(), 0.0, r.cvLimit(), 0.0,
            r.equipment(), r.analyst(), "APROVADO", false, List.of(), Instant.now(), Instant.now(),
            null, null, null, null
        );
    }

    @SuppressWarnings("unused")
    private TransactionStatus anyTx() { return null; }

    // ===== Fakes (evita Mockito de classes concretas, incompativel com Java 25) =====

    static class FakeQcService extends QcService {
        final Map<QcRecordRequest, QcRecordResponse> responses = new HashMap<>();
        final Map<QcRecordRequest, RuntimeException> errors = new HashMap<>();
        List<QcRecordResponse> atomicResponse = List.of();

        FakeQcService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public QcRecordResponse createRecord(QcRecordRequest request) {
            if (errors.containsKey(request)) throw errors.get(request);
            QcRecordResponse r = responses.get(request);
            if (r == null) throw new BusinessException("sem resposta mocada");
            return r;
        }

        @Override
        public List<QcRecordResponse> createRecordsBatch(List<QcRecordRequest> requests) {
            return atomicResponse;
        }
    }

    static class FakeImportRunService extends ImportRunService {
        String lastMode;
        int lastSuccess;
        int lastFailure;

        FakeImportRunService() {
            super(null);
        }

        @Override
        public ImportRun record(String source, String mode, int total, int success, int failure,
            long durationMs, String errorSummary, Authentication authentication) {
            this.lastMode = mode;
            this.lastSuccess = success;
            this.lastFailure = failure;
            return ImportRun.builder().id(UUID.randomUUID()).mode(mode).totalRows(total)
                .successRows(success).failureRows(failure).status("x").source(source).build();
        }

        @Override
        public List<ImportRunResponse> history(int limit) {
            return List.of();
        }
    }

    /** Executa o callback do TransactionTemplate de forma sincrona, sem abrir conexao. */
    static class FakeTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus(true);
        }

        @Override
        public void commit(TransactionStatus status) { /* no-op */ }

        @Override
        public void rollback(TransactionStatus status) { /* no-op */ }
    }
}
