package com.bioqc.service;

import com.bioqc.dto.request.QcReferenceRequest;
import com.bioqc.entity.QcExam;
import com.bioqc.entity.QcReferenceValue;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.QcExamRepository;
import com.bioqc.repository.QcReferenceValueRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QcReferenceService {

    private final QcReferenceValueRepository qcReferenceValueRepository;
    private final QcExamRepository qcExamRepository;

    public QcReferenceService(
        QcReferenceValueRepository qcReferenceValueRepository,
        QcExamRepository qcExamRepository
    ) {
        this.qcReferenceValueRepository = qcReferenceValueRepository;
        this.qcExamRepository = qcExamRepository;
    }

    @Transactional(readOnly = true)
    public List<QcReferenceValue> getReferences(UUID examId, Boolean activeOnly) {
        boolean onlyActive = Boolean.TRUE.equals(activeOnly);
        List<QcReferenceValue> results;
        if (examId != null && onlyActive) {
            results = qcReferenceValueRepository.findByExamIdAndIsActiveTrue(examId);
        } else if (examId != null) {
            results = qcReferenceValueRepository.findAll().stream()
                .filter(reference -> reference.getExam() != null && examId.equals(reference.getExam().getId()))
                .toList();
        } else if (onlyActive) {
            results = qcReferenceValueRepository.findByIsActiveTrue();
        } else {
            results = qcReferenceValueRepository.findAll();
        }
        // Forçar inicialização do exam LAZY dentro da transação (open-in-view=false em prod)
        results.forEach(ref -> {
            if (ref.getExam() != null) {
                ref.getExam().getName();
            }
        });
        return results;
    }

    @Transactional(readOnly = true)
    public QcReferenceValue resolveApplicableReference(
        String examName,
        String area,
        String level,
        LocalDate referenceDate,
        String lotNumber,
        UUID referenceId
    ) {
        String normalizedExamName = normalizeRequired(examName, "exame");
        String normalizedArea = normalizeRequired(area, "área");
        String normalizedLevel = normalizeRequired(level, "nível");
        String normalizedLotNumber = normalizeNullable(lotNumber);
        LocalDate effectiveDate = referenceDate != null ? referenceDate : LocalDate.now();

        if (referenceId != null) {
            return validateExplicitReference(
                referenceId,
                normalizedExamName,
                normalizedArea,
                normalizedLevel,
                effectiveDate,
                normalizedLotNumber
            );
        }

        List<QcReferenceValue> validCandidates = qcReferenceValueRepository
            .findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
                normalizedExamName,
                normalizedArea,
                normalizedLevel
            )
            .stream()
            .filter(reference -> isApplicableOnDate(reference, effectiveDate))
            .toList();

        List<QcReferenceValue> genericCandidates = validCandidates.stream()
            .filter(this::isLotAgnostic)
            .toList();

        if (normalizedLotNumber != null) {
            List<QcReferenceValue> exactLotCandidates = validCandidates.stream()
                .filter(reference -> lotMatches(reference, normalizedLotNumber))
                .toList();
            if (!exactLotCandidates.isEmpty()) {
                return requireSingleCandidate(
                    exactLotCandidates,
                    "Existe mais de uma referência ativa e vigente para o mesmo exame, área, nível e lote informados."
                );
            }
            if (!genericCandidates.isEmpty()) {
                return requireSingleCandidate(
                    genericCandidates,
                    "Existe mais de uma referência ativa e vigente para o mesmo exame, área e nível informados sem distinção de lote."
                );
            }
            throw new BusinessException(
                "Nenhuma referência válida foi encontrada para o exame, área, nível, lote e data informados."
            );
        }

        if (!genericCandidates.isEmpty()) {
            return requireSingleCandidate(
                genericCandidates,
                "Existe mais de uma referência ativa e vigente para o mesmo exame, área e nível informados."
            );
        }
        if (!validCandidates.isEmpty()) {
            throw new BusinessException(
                "Existem referências vigentes dependentes de lote para o exame, área e nível informados. Informe o lote do controle ou selecione uma referência explícita."
            );
        }
        throw new BusinessException(
            "Nenhuma referência válida foi encontrada para o exame, área, nível e data informados."
        );
    }

    @Transactional
    public QcReferenceValue createReference(QcReferenceRequest request) {
        validateReferenceRequest(request);
        closePreviousReference(request.examId(), request.level(), request.validFrom());
        checkOverlap(request.examId(), request.level(), request.validFrom(), request.validUntil(), null);
        QcExam exam = qcExamRepository.findById(request.examId())
            .orElseThrow(() -> new ResourceNotFoundException("Exame não encontrado"));
        QcReferenceValue reference = QcReferenceValue.builder()
            .exam(exam)
            .name(request.name())
            .level(request.level())
            .lotNumber(normalizeNullable(request.lotNumber()))
            .manufacturer(request.manufacturer())
            .targetValue(request.targetValue())
            .targetSd(request.targetSd())
            .cvMaxThreshold(request.cvMaxThreshold())
            .validFrom(request.validFrom())
            .validUntil(request.validUntil())
            .notes(request.notes())
            .isActive(Boolean.TRUE)
            .build();
        return qcReferenceValueRepository.save(reference);
    }

    private void closePreviousReference(UUID examId, String level, LocalDate newValidFrom) {
        if (newValidFrom == null) {
            return;
        }
        LocalDate closingDate = newValidFrom.minusDays(1);
        qcReferenceValueRepository.findByExamIdAndIsActiveTrue(examId).stream()
            .filter(ref -> ref.getLevel() != null && ref.getLevel().equalsIgnoreCase(level))
            .filter(ref -> ref.getValidUntil() == null)
            .filter(ref -> ref.getValidFrom() == null || !ref.getValidFrom().isAfter(closingDate))
            .forEach(ref -> {
                ref.setValidUntil(closingDate);
                qcReferenceValueRepository.save(ref);
            });
    }

    @Transactional
    public QcReferenceValue updateReference(UUID id, QcReferenceRequest request) {
        validateReferenceRequest(request);
        checkOverlap(request.examId(), request.level(), request.validFrom(), request.validUntil(), id);
        QcReferenceValue reference = qcReferenceValueRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Referência não encontrada"));
        QcExam exam = qcExamRepository.findById(request.examId())
            .orElseThrow(() -> new ResourceNotFoundException("Exame não encontrado"));
        reference.setExam(exam);
        reference.setName(request.name());
        reference.setLevel(request.level());
        reference.setLotNumber(normalizeNullable(request.lotNumber()));
        reference.setManufacturer(request.manufacturer());
        reference.setTargetValue(request.targetValue());
        reference.setTargetSd(request.targetSd());
        reference.setCvMaxThreshold(request.cvMaxThreshold());
        reference.setValidFrom(request.validFrom());
        reference.setValidUntil(request.validUntil());
        reference.setNotes(request.notes());
        return qcReferenceValueRepository.save(reference);
    }

    @Transactional
    public void deleteReference(UUID id) {
        if (!qcReferenceValueRepository.existsById(id)) {
            throw new ResourceNotFoundException("Referência não encontrada");
        }
        qcReferenceValueRepository.deleteById(id);
    }

    private QcReferenceValue validateExplicitReference(
        UUID referenceId,
        String examName,
        String area,
        String level,
        LocalDate referenceDate,
        String lotNumber
    ) {
        QcReferenceValue reference = qcReferenceValueRepository.findById(referenceId)
            .orElseThrow(() -> new ResourceNotFoundException("Referência de CQ não encontrada"));

        if (!Boolean.TRUE.equals(reference.getIsActive())) {
            throw new BusinessException("A referência selecionada está inativa.");
        }
        if (reference.getExam() == null || reference.getExam().getName() == null) {
            throw new BusinessException("A referência selecionada não possui exame associado.");
        }
        if (!reference.getExam().getName().equalsIgnoreCase(examName)) {
            throw new BusinessException("A referência selecionada não pertence ao exame informado.");
        }
        if (reference.getExam().getArea() == null || !reference.getExam().getArea().equalsIgnoreCase(area)) {
            throw new BusinessException("A referência selecionada não pertence à área informada.");
        }
        if (!normalizeRequired(reference.getLevel(), "nível da referência").equalsIgnoreCase(level)) {
            throw new BusinessException("A referência selecionada não pertence ao nível informado.");
        }
        if (!isApplicableOnDate(reference, referenceDate)) {
            throw new BusinessException("A referência selecionada não está vigente para a data informada.");
        }

        String referenceLotNumber = normalizeNullable(reference.getLotNumber());
        if (referenceLotNumber != null && lotNumber != null && !referenceLotNumber.equalsIgnoreCase(lotNumber)) {
            throw new BusinessException("A referência selecionada não é compatível com o lote informado.");
        }
        return reference;
    }

    private QcReferenceValue requireSingleCandidate(List<QcReferenceValue> candidates, String ambiguityMessage) {
        if (candidates.size() > 1) {
            throw new BusinessException(ambiguityMessage);
        }
        return candidates.get(0);
    }

    private boolean isApplicableOnDate(QcReferenceValue reference, LocalDate referenceDate) {
        LocalDate validFrom = reference.getValidFrom();
        LocalDate validUntil = reference.getValidUntil();
        return (validFrom == null || !validFrom.isAfter(referenceDate))
            && (validUntil == null || !validUntil.isBefore(referenceDate));
    }

    private boolean lotMatches(QcReferenceValue reference, String lotNumber) {
        String referenceLotNumber = normalizeNullable(reference.getLotNumber());
        return referenceLotNumber != null && referenceLotNumber.equalsIgnoreCase(lotNumber);
    }

    private boolean isLotAgnostic(QcReferenceValue reference) {
        return normalizeNullable(reference.getLotNumber()) == null;
    }

    private void checkOverlap(UUID examId, String level, LocalDate validFrom,
                               LocalDate validUntil, UUID excludeId) {
        LocalDate effectiveFrom = validFrom != null ? validFrom : LocalDate.MIN;
        LocalDate effectiveUntil = validUntil != null ? validUntil : LocalDate.of(9999, 12, 31);
        List<QcReferenceValue> overlapping = qcReferenceValueRepository.findOverlapping(
            examId, level, effectiveFrom, effectiveUntil, excludeId
        );
        if (!overlapping.isEmpty()) {
            throw new BusinessException(
                "Já existe uma referência ativa para este exame e nível com período de validade sobreposto."
            );
        }
    }

    private void validateReferenceRequest(QcReferenceRequest request) {
        if (request.validFrom() != null && request.validUntil() != null && request.validUntil().isBefore(request.validFrom())) {
            throw new BusinessException("A validade final da referência não pode ser anterior à validade inicial.");
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException("O campo " + fieldName + " é obrigatório para resolver a referência.");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
