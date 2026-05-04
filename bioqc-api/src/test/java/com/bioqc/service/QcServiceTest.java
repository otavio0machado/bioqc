package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bioqc.dto.request.QcRecordRequest;
import com.bioqc.entity.QcExam;
import com.bioqc.entity.QcRecord;
import com.bioqc.entity.QcReferenceValue;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.QcExamRepository;
import com.bioqc.repository.QcRecordRepository;
import com.bioqc.repository.QcReferenceValueRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class QcServiceTest {

    private QcService qcService;

    @Mock
    private QcRecordRepository recordRepository;

    @Mock
    private QcReferenceValueRepository referenceRepository;

    @Mock
    private QcExamRepository examRepository;

    @Mock
    private com.bioqc.repository.PostCalibrationRecordRepository postCalibrationRecordRepository;

    @BeforeEach
    void setUp() {
        QcReferenceService referenceService = new QcReferenceService(referenceRepository, examRepository);
        qcService = new QcService(recordRepository, referenceService, new WestgardEngine(), examRepository,
            new AuditService(null, null, new com.fasterxml.jackson.databind.ObjectMapper()),
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
            postCalibrationRecordRepository);
    }

    @Test
    @DisplayName("deve criar registro aprovado")
    void shouldCreateRecordWithApprovedStatus() {
        QcRecordRequest request = request();
        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference()));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.status()).isEqualTo("APROVADO");
    }

    @Test
    @DisplayName("deve criar registro reprovado quando Westgard rejeita")
    void shouldCreateRecordWithRejectedStatusWhenWestgardFails() {
        QcRecordRequest request = new QcRecordRequest(
            "Glicose", "bioquimica", LocalDate.now(), "Normal", "L1", 116D, 100D, 5D, 10D, "AU680", "Ana", null
        );
        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference()));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.status()).isEqualTo("REPROVADO");
    }

    @Test
    @DisplayName("deve criar registro em alerta quando Westgard retornar apenas warning")
    void shouldCreateRecordWithAlertStatusWhenWestgardWarns() {
        QcRecordRequest request = new QcRecordRequest(
            "Glicose", "bioquimica", LocalDate.now(), "Normal", "L1", 111D, 100D, 5D, 10D, "AU680", "Ana", null
        );
        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference()));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.status()).isEqualTo("ALERTA");
        assertThat(response.violations()).extracting(com.bioqc.dto.response.ViolationResponse::rule)
            .containsExactly("1-2s");
    }

    @Test
    @DisplayName("deve marcar necessidade de calibração quando CV passa do limite")
    void shouldSetNeedsCalibrationWhenCvExceedsLimit() {
        QcRecordRequest request = new QcRecordRequest(
            "Glicose", "bioquimica", LocalDate.now(), "Normal", "L1", 130D, 100D, 5D, 10D, "AU680", "Ana", null
        );
        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference()));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.needsCalibration()).isTrue();
    }

    @Test
    @DisplayName("não deve marcar necessidade de calibração quando CV é igual ao limite")
    void shouldNotSetNeedsCalibrationWhenCvMatchesLimit() {
        QcRecordRequest request = new QcRecordRequest(
            "Glicose", "bioquimica", LocalDate.now(), "Normal", "L1", 110D, 100D, 5D, 10D, "AU680", "Ana", null
        );
        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference()));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.needsCalibration()).isFalse();
    }

    @Test
    @DisplayName("deve calcular CV corretamente")
    void shouldCalculateCvCorrectly() {
        QcRecordRequest request = request();
        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference()));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.cv()).isEqualTo(5D);
    }

    @Test
    @DisplayName("deve calcular Z-score corretamente")
    void shouldCalculateZScoreCorrectly() {
        QcRecordRequest request = request();
        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference()));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.zScore()).isEqualTo(1D);
    }

    @Test
    @DisplayName("deve manter needsCalibration falso quando Westgard reprova mas o CV fica abaixo do limite")
    void shouldKeepNeedsCalibrationFalseWhenRejectedStatusDoesNotExceedCvLimit() {
        QcRecordRequest request = new QcRecordRequest(
            "Glicose", "bioquimica", LocalDate.now(), "Normal", "L1", 116D, 100D, 5D, 20D, "AU680", "Ana", null
        );
        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference()));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.status()).isEqualTo("REPROVADO");
        assertThat(response.needsCalibration()).isFalse();
    }

    @Test
    @DisplayName("deve retornar z-score zero e alerta quando o desvio padrão alvo for zero")
    void shouldReturnZeroZScoreAndAlertWhenTargetSdIsZero() {
        QcRecordRequest request = request();
        QcReferenceValue reference = reference();
        reference.setTargetSd(0D);

        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.zScore()).isEqualTo(0D);
        assertThat(response.status()).isEqualTo("ALERTA");
        assertThat(response.violations()).extracting(com.bioqc.dto.response.ViolationResponse::rule)
            .containsExactly("SD=0");
    }

    @Test
    @DisplayName("deve bloquear registro quando não há referência válida")
    void shouldRejectRecordWhenNoValidReferenceExists() {
        QcRecordRequest request = request();
        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of());

        assertThatThrownBy(() -> qcService.createRecord(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Nenhuma referência válida");
    }

    @Test
    @DisplayName("deve propagar o lote da referência quando o request vier sem lote")
    void shouldUseReferenceLotWhenRequestLotIsBlank() {
        QcRecordRequest request = new QcRecordRequest(
            "Glicose",
            "bioquimica",
            LocalDate.now(),
            "Normal",
            " ",
            105D,
            100D,
            5D,
            10D,
            "AU680",
            "Ana",
            UUID.randomUUID()
        );
        QcReferenceValue reference = reference();
        reference.setLotNumber("REF-LOT-01");

        mockExam();
        when(referenceRepository.findById(request.referenceId())).thenReturn(java.util.Optional.of(reference));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.lotNumber()).isEqualTo("REF-LOT-01");
        assertThat(response.referenceId()).isEqualTo(reference.getId());
    }

    @Test
    @DisplayName("deve preservar cvLimit informado no request como limite de calibração")
    void shouldPreserveProvidedCvLimitAsCalibrationThreshold() {
        QcRecordRequest request = new QcRecordRequest(
            "Glicose", "bioquimica", LocalDate.now(), "Normal", "L1", 105D, 100D, 5D, 99D, "AU680", "Ana", null
        );
        QcReferenceValue reference = reference();
        reference.setCvMaxThreshold(10D);

        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.cvLimit()).isEqualTo(99D);
        assertThat(response.needsCalibration()).isFalse();
    }

    @Test
    @DisplayName("deve retornar ALERTA quando Westgard gerar apenas warnings")
    void shouldReturnAlertWhenWestgardGeneratesOnlyWarnings() {
        QcRecordRequest request = new QcRecordRequest(
            "Glicose", "bioquimica", LocalDate.now(), "Normal", "L1", 111D, 100D, 5D, 20D, "AU680", "Ana", null
        );
        QcReferenceValue reference = reference();
        reference.setCvMaxThreshold(20D);

        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.status()).isEqualTo("ALERTA");
        assertThat(response.needsCalibration()).isFalse();
        assertThat(response.violations()).extracting(com.bioqc.dto.response.ViolationResponse::rule)
            .containsExactly("1-2s");
    }

    @Test
    @DisplayName("deve retornar registros filtrando por área")
    void shouldReturnRecordsFilteredByArea() {
        when(recordRepository.findByFilters(eq("bioquimica"), eq(null), eq(null), eq(null))).thenReturn(List.of(
            QcRecord.builder().id(UUID.randomUUID()).examName("Glicose").area("bioquimica").date(LocalDate.now()).build()
        ));

        var response = qcService.getRecords("bioquimica", null, null, null);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().area()).isEqualTo("bioquimica");
    }

    @Test
    @DisplayName("deve lançar exceção quando registro não existe")
    void shouldThrowNotFoundWhenRecordDoesNotExist() {
        when(recordRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> qcService.getRecord(UUID.randomUUID()))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deve bloquear importação em lote vazia")
    void shouldRejectEmptyBatchImport() {
        assertThatThrownBy(() -> qcService.createRecordsBatch(List.of()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Nenhum registro");
    }

    @Test
    @DisplayName("deve informar a linha quando um item do lote falha")
    void shouldIncludeRowNumberWhenBatchItemFails() {
        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference()));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());

        QcRecordRequest invalid = new QcRecordRequest(
            "Exame inexistente",
            "bioquimica",
            LocalDate.now(),
            "Normal",
            "L1",
            100D,
            100D,
            5D,
            10D,
            "AU680",
            "Ana",
            null
        );

        assertThatThrownBy(() -> qcService.createRecordsBatch(List.of(request(), invalid)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Linha 2");
    }

    @Test
    @DisplayName("deve buscar histórico do Westgard pela referência resolvida e data da medição")
    void shouldLoadWestgardHistoryUsingResolvedReferenceAndMeasurementDate() {
        QcRecordRequest request = request();
        QcReferenceValue reference = reference();

        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference));
        when(recordRepository.findWestgardHistory(
            eq(reference.getId()),
            eq("Glicose"),
            eq("Normal"),
            eq("bioquimica"),
            eq(request.date()),
            isNull(),
            any(Pageable.class)
        )).thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        qcService.createRecord(request);

        verify(recordRepository).findWestgardHistory(
            eq(reference.getId()),
            eq("Glicose"),
            eq("Normal"),
            eq("bioquimica"),
            eq(request.date()),
            isNull(),
            any(Pageable.class)
        );
    }

    @Test
    @DisplayName("deve excluir o próprio registro ao recalcular histórico no update")
    void shouldExcludeCurrentRecordFromWestgardHistoryWhenUpdating() {
        UUID recordId = UUID.randomUUID();
        QcRecordRequest request = request();
        QcReferenceValue reference = reference();

        when(recordRepository.findById(recordId)).thenReturn(Optional.of(
            QcRecord.builder()
                .id(recordId)
                .examName("Glicose")
                .area("bioquimica")
                .level("Normal")
                .date(request.date())
                .build()
        ));
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference));
        when(recordRepository.findWestgardHistory(
            eq(reference.getId()),
            eq("Glicose"),
            eq("Normal"),
            eq("bioquimica"),
            eq(request.date()),
            eq(recordId),
            any(Pageable.class)
        )).thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        qcService.updateRecord(recordId, request);

        verify(recordRepository).findWestgardHistory(
            eq(reference.getId()),
            eq("Glicose"),
            eq("Normal"),
            eq("bioquimica"),
            eq(request.date()),
            eq(recordId),
            any(Pageable.class)
        );
    }

    @Test
    @DisplayName("deve ignorar histórico de lote diferente quando o registro atual possui lote")
    void shouldIgnoreHistoryWithDifferentLotWhenCurrentRecordHasLot() {
        QcRecordRequest request = new QcRecordRequest(
            "Glicose", "bioquimica", LocalDate.now(), "Normal", "L1", 111D, 100D, 5D, 10D, "AU680", "Ana", null
        );
        QcReferenceValue reference = reference();

        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of(
                historyRecord(reference, "L2", request.date().minusDays(1), Instant.parse("2026-04-03T10:00:00Z"), 112D)
            ));
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.status()).isEqualTo("ALERTA");
        assertThat(response.violations()).extracting(com.bioqc.dto.response.ViolationResponse::rule)
            .containsExactly("1-2s");
    }

    @Test
    @DisplayName("deve usar histórico do mesmo lote para detectar 2-2s")
    void shouldUseSameLotHistoryToDetect22s() {
        QcRecordRequest request = new QcRecordRequest(
            "Glicose", "bioquimica", LocalDate.now(), "Normal", "L1", 111D, 100D, 5D, 10D, "AU680", "Ana", null
        );
        QcReferenceValue reference = reference();

        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of(
                historyRecord(reference, "L1", request.date().minusDays(1), Instant.parse("2026-04-03T10:00:00Z"), 112D)
            ));
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.status()).isEqualTo("REPROVADO");
        assertThat(response.violations()).extracting(com.bioqc.dto.response.ViolationResponse::rule)
            .contains("2-2s");
    }

    @Test
    @DisplayName("deve ignorar histórico com data futura ao registro avaliado")
    void shouldIgnoreFutureDatedHistory() {
        QcRecordRequest request = new QcRecordRequest(
            "Glicose", "bioquimica", LocalDate.now(), "Normal", "L1", 111D, 100D, 5D, 10D, "AU680", "Ana", null
        );
        QcReferenceValue reference = reference();

        mockExam();
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of(
                historyRecord(reference, "L1", request.date().plusDays(1), Instant.parse("2026-04-05T10:00:00Z"), 112D)
            ));
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.createRecord(request);

        assertThat(response.status()).isEqualTo("ALERTA");
        assertThat(response.violations()).extracting(com.bioqc.dto.response.ViolationResponse::rule)
            .containsExactly("1-2s");
    }

    @Test
    @DisplayName("deve ignorar histórico do mesmo dia criado após o registro em atualização")
    void shouldIgnoreSameDayHistoryCreatedAfterUpdatedRecord() {
        UUID recordId = UUID.randomUUID();
        QcRecordRequest request = new QcRecordRequest(
            "Glicose", "bioquimica", LocalDate.now(), "Normal", "L1", 111D, 100D, 5D, 10D, "AU680", "Ana", null
        );
        QcReferenceValue reference = reference();
        Instant currentCreatedAt = Instant.parse("2026-04-04T12:00:00Z");

        when(recordRepository.findById(recordId)).thenReturn(Optional.of(
            QcRecord.builder()
                .id(recordId)
                .reference(reference)
                .examName("Glicose")
                .area("bioquimica")
                .level("Normal")
                .date(request.date())
                .lotNumber("L1")
                .createdAt(currentCreatedAt)
                .build()
        ));
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of(
                historyRecord(reference, "L1", request.date(), Instant.parse("2026-04-04T13:00:00Z"), 112D)
            ));
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.updateRecord(recordId, request);

        assertThat(response.status()).isEqualTo("ALERTA");
        assertThat(response.violations()).extracting(com.bioqc.dto.response.ViolationResponse::rule)
            .containsExactly("1-2s");
    }

    @Test
    @DisplayName("deve recalcular status e violações no update")
    void shouldRecalculateStatusAndViolationsOnUpdate() {
        UUID recordId = UUID.randomUUID();
        QcRecordRequest request = request();
        QcReferenceValue reference = reference();

        when(recordRepository.findById(recordId)).thenReturn(Optional.of(
            QcRecord.builder()
                .id(recordId)
                .reference(reference)
                .examName("Glicose")
                .area("bioquimica")
                .level("Normal")
                .date(request.date())
                .lotNumber("L1")
                .status("REPROVADO")
                .violations(List.of(
                    com.bioqc.entity.WestgardViolation.builder()
                        .rule("1-3s")
                        .description("Erro Aleatório: Valor excede 3 SD.")
                        .severity("REJECTION")
                        .build()
                ))
                .build()
        ));
        when(referenceRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(reference));
        when(recordRepository.findWestgardHistory(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());
        when(recordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = qcService.updateRecord(recordId, request);

        assertThat(response.status()).isEqualTo("APROVADO");
        assertThat(response.violations()).isEmpty();
    }

    private QcRecordRequest request() {
        return new QcRecordRequest(
            "Glicose", "bioquimica", LocalDate.now(), "Normal", "L1", 105D, 100D, 5D, 10D, "AU680", "Ana", null
        );
    }

    private QcReferenceValue reference() {
        return QcReferenceValue.builder()
            .id(UUID.randomUUID())
            .exam(QcExam.builder().id(UUID.randomUUID()).name("Glicose").area("bioquimica").isActive(Boolean.TRUE).build())
            .name("Controle Glicose N1")
            .level("Normal")
            .targetValue(100D)
            .targetSd(5D)
            .cvMaxThreshold(10D)
            .isActive(Boolean.TRUE)
            .build();
    }

    private QcRecord historyRecord(
        QcReferenceValue reference,
        String lotNumber,
        LocalDate date,
        Instant createdAt,
        double value
    ) {
        return QcRecord.builder()
            .id(UUID.randomUUID())
            .reference(reference)
            .examName("Glicose")
            .area("bioquimica")
            .date(date)
            .level("Normal")
            .lotNumber(lotNumber)
            .value(value)
            .targetValue(100D)
            .targetSd(5D)
            .createdAt(createdAt)
            .build();
    }

    private void mockExam() {
        when(examRepository.findByAreaAndIsActiveTrue("bioquimica")).thenReturn(List.of(
            QcExam.builder().name("Glicose").area("bioquimica").build()
        ));
    }
}
