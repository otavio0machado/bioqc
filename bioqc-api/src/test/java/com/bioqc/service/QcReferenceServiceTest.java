package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bioqc.dto.request.QcReferenceRequest;
import com.bioqc.entity.QcExam;
import com.bioqc.entity.QcReferenceValue;
import com.bioqc.exception.BusinessException;
import com.bioqc.repository.QcExamRepository;
import com.bioqc.repository.QcReferenceValueRepository;
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

@ExtendWith(MockitoExtension.class)
class QcReferenceServiceTest {

    private QcReferenceService qcReferenceService;

    @Mock
    private QcReferenceValueRepository qcReferenceValueRepository;

    @Mock
    private QcExamRepository qcExamRepository;

    @BeforeEach
    void setUp() {
        qcReferenceService = new QcReferenceService(qcReferenceValueRepository, qcExamRepository);
    }

    @Test
    @DisplayName("deve priorizar referência de lote exato quando existir")
    void shouldResolveExactLotReferenceWhenLotMatches() {
        QcReferenceValue exactLot = reference("LOT-01", LocalDate.of(2026, 4, 1), null);
        QcReferenceValue generic = reference(null, LocalDate.of(2026, 3, 1), null);

        when(qcReferenceValueRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(generic, exactLot));

        QcReferenceValue resolved = qcReferenceService.resolveApplicableReference(
            "Glicose",
            "bioquimica",
            "Normal",
            LocalDate.of(2026, 4, 3),
            "LOT-01",
            null
        );

        assertThat(resolved.getId()).isEqualTo(exactLot.getId());
    }

    @Test
    @DisplayName("deve usar referência genérica quando lote não for informado e houver uma única válida")
    void shouldResolveGenericReferenceWhenLotIsAbsent() {
        QcReferenceValue generic = reference(null, LocalDate.of(2026, 4, 1), null);

        when(qcReferenceValueRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(generic));

        QcReferenceValue resolved = qcReferenceService.resolveApplicableReference(
            "Glicose",
            "bioquimica",
            "Normal",
            LocalDate.of(2026, 4, 3),
            null,
            null
        );

        assertThat(resolved.getId()).isEqualTo(generic.getId());
    }

    @Test
    @DisplayName("deve bloquear conflito entre múltiplas referências do mesmo lote")
    void shouldRejectWhenMoreThanOneLotSpecificReferenceMatches() {
        QcReferenceValue first = reference("LOT-01", LocalDate.of(2026, 4, 1), null);
        QcReferenceValue second = reference("LOT-01", LocalDate.of(2026, 3, 15), null);

        when(qcReferenceValueRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> qcReferenceService.resolveApplicableReference(
            "Glicose",
            "bioquimica",
            "Normal",
            LocalDate.of(2026, 4, 3),
            "LOT-01",
            null
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("mesmo exame, área, nível e lote");
    }

    @Test
    @DisplayName("deve exigir lote quando só existirem referências vigentes dependentes de lote")
    void shouldRejectWhenOnlyLotSpecificReferencesExistAndLotIsMissing() {
        QcReferenceValue lotSpecific = reference("LOT-01", LocalDate.of(2026, 4, 1), null);

        when(qcReferenceValueRepository.findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
            "Glicose",
            "bioquimica",
            "Normal"
        )).thenReturn(List.of(lotSpecific));

        assertThatThrownBy(() -> qcReferenceService.resolveApplicableReference(
            "Glicose",
            "bioquimica",
            "Normal",
            LocalDate.of(2026, 4, 3),
            null,
            null
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("dependentes de lote");
    }

    @Test
    @DisplayName("deve bloquear referência explícita fora da vigência")
    void shouldRejectExplicitReferenceOutsideValidity() {
        UUID referenceId = UUID.randomUUID();
        QcReferenceValue expiredReference = reference("LOT-01", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1));
        expiredReference.setId(referenceId);

        when(qcReferenceValueRepository.findById(referenceId)).thenReturn(Optional.of(expiredReference));

        assertThatThrownBy(() -> qcReferenceService.resolveApplicableReference(
            "Glicose",
            "bioquimica",
            "Normal",
            LocalDate.of(2026, 4, 3),
            "LOT-01",
            referenceId
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("não está vigente");
    }

    @Test
    @DisplayName("deve validar faixa de validade ao criar referência")
    void shouldRejectReferenceWithInvalidValidityRange() {
        QcReferenceRequest request = new QcReferenceRequest(
            UUID.randomUUID(),
            "Controle Glicose N1",
            "Normal",
            "LOT-01",
            "Fabricante",
            100D,
            5D,
            10D,
            LocalDate.of(2026, 4, 10),
            LocalDate.of(2026, 4, 1),
            "Referência inválida"
        );

        assertThatThrownBy(() -> qcReferenceService.createReference(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("validade final");
    }

    private QcReferenceValue reference(String lotNumber, LocalDate validFrom, LocalDate validUntil) {
        return QcReferenceValue.builder()
            .id(UUID.randomUUID())
            .exam(QcExam.builder().id(UUID.randomUUID()).name("Glicose").area("bioquimica").isActive(Boolean.TRUE).build())
            .name("Controle Glicose N1")
            .level("Normal")
            .lotNumber(lotNumber)
            .targetValue(100D)
            .targetSd(5D)
            .cvMaxThreshold(10D)
            .validFrom(validFrom)
            .validUntil(validUntil)
            .isActive(Boolean.TRUE)
            .build();
    }
}
