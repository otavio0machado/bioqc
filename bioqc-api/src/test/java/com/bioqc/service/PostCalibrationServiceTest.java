package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bioqc.dto.request.PostCalibrationRequest;
import com.bioqc.entity.PostCalibrationRecord;
import com.bioqc.entity.QcRecord;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.PostCalibrationRecordRepository;
import com.bioqc.repository.QcRecordRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostCalibrationServiceTest {

    private PostCalibrationService postCalibrationService;

    @Mock
    private PostCalibrationRecordRepository postCalibrationRecordRepository;

    @Mock
    private QcRecordRepository qcRecordRepository;

    @BeforeEach
    void setUp() {
        postCalibrationService = new PostCalibrationService(postCalibrationRecordRepository, qcRecordRepository);
    }

    @Test
    @DisplayName("deve registrar pós-calibração e encerrar pendência corretiva")
    void shouldCreatePostCalibrationAndCloseCorrectivePending() {
        UUID recordId = UUID.randomUUID();
        QcRecord original = qcRecord(recordId, true, "REPROVADO", 112D, 12D);
        PostCalibrationRequest request = new PostCalibrationRequest(LocalDate.of(2026, 4, 4), 101D, "Ana", "Recalibração ok");

        when(qcRecordRepository.findById(recordId)).thenReturn(Optional.of(original));
        when(postCalibrationRecordRepository.findByQcRecordId(recordId)).thenReturn(Optional.empty());
        when(qcRecordRepository.save(any(QcRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postCalibrationRecordRepository.save(any(PostCalibrationRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostCalibrationRecord result = postCalibrationService.createPostCalibration(recordId, request);

        assertThat(result.getQcRecord()).isEqualTo(original);
        assertThat(result.getOriginalValue()).isEqualTo(112D);
        assertThat(result.getOriginalCv()).isEqualTo(12D);
        assertThat(result.getPostCalibrationValue()).isEqualTo(101D);
        assertThat(result.getPostCalibrationCv()).isEqualTo(1D);
        assertThat(original.getNeedsCalibration()).isFalse();
        assertThat(original.getStatus()).isEqualTo("REPROVADO");
        verify(qcRecordRepository).save(original);
        verify(postCalibrationRecordRepository).save(any(PostCalibrationRecord.class));
    }

    @Test
    @DisplayName("deve bloquear pós-calibração sem pendência corretiva ativa")
    void shouldRejectPostCalibrationWhenThereIsNoCorrectivePending() {
        UUID recordId = UUID.randomUUID();
        QcRecord original = qcRecord(recordId, false, "ALERTA", 111D, 8D);

        when(qcRecordRepository.findById(recordId)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> postCalibrationService.createPostCalibration(recordId, request()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("pendência corretiva ativa");

        verify(postCalibrationRecordRepository, never()).save(any(PostCalibrationRecord.class));
    }

    @Test
    @DisplayName("deve bloquear duplicidade de pós-calibração para o mesmo evento")
    void shouldRejectDuplicatePostCalibrationForSameRecord() {
        UUID recordId = UUID.randomUUID();
        QcRecord original = qcRecord(recordId, true, "REPROVADO", 112D, 12D);

        when(qcRecordRepository.findById(recordId)).thenReturn(Optional.of(original));
        when(postCalibrationRecordRepository.findByQcRecordId(recordId))
            .thenReturn(Optional.of(PostCalibrationRecord.builder().id(UUID.randomUUID()).qcRecord(original).build()));

        assertThatThrownBy(() -> postCalibrationService.createPostCalibration(recordId, request()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Já existe uma pós-calibração");

        verify(postCalibrationRecordRepository, never()).save(any(PostCalibrationRecord.class));
    }

    @Test
    @DisplayName("deve retornar 404 quando o registro de CQ não existe")
    void shouldThrowNotFoundWhenOriginalRecordDoesNotExist() {
        UUID recordId = UUID.randomUUID();
        when(qcRecordRepository.findById(recordId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postCalibrationService.createPostCalibration(recordId, request()))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Registro de CQ não encontrado");
    }

    private PostCalibrationRequest request() {
        return new PostCalibrationRequest(LocalDate.of(2026, 4, 4), 101D, "Ana", "Recalibração ok");
    }

    private QcRecord qcRecord(UUID id, boolean needsCalibration, String status, double value, double cv) {
        return QcRecord.builder()
            .id(id)
            .examName("Glicose")
            .area("bioquimica")
            .date(LocalDate.of(2026, 4, 4))
            .level("Normal")
            .lotNumber("L1")
            .value(value)
            .targetValue(100D)
            .targetSd(5D)
            .cv(cv)
            .cvLimit(10D)
            .zScore(2.4D)
            .status(status)
            .needsCalibration(needsCalibration)
            .build();
    }
}
