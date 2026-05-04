package com.bioqc.service;

import com.bioqc.dto.request.QcRecordRequest;
import com.bioqc.dto.response.LeveyJenningsResponse;
import com.bioqc.dto.response.QcRecordResponse;
import com.bioqc.entity.PostCalibrationRecord;
import com.bioqc.entity.QcExam;
import com.bioqc.entity.QcRecord;
import com.bioqc.entity.QcReferenceValue;
import com.bioqc.entity.WestgardViolation;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.PostCalibrationRecordRepository;
import com.bioqc.repository.QcExamRepository;
import com.bioqc.repository.QcRecordRepository;
import com.bioqc.util.NumericUtils;
import com.bioqc.util.ResponseMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QcService {

    private static final double DEFAULT_CV_LIMIT = 10D;
    private static final int MAX_BATCH_SIZE = 1000;
    private static final int WESTGARD_HISTORY_LIMIT = 10;
    private static final int WESTGARD_HISTORY_FETCH_SIZE = 50;

    private final QcRecordRepository qcRecordRepository;
    private final QcReferenceService qcReferenceService;
    private final WestgardEngine westgardEngine;
    private final QcExamRepository qcExamRepository;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    private final PostCalibrationRecordRepository postCalibrationRecordRepository;

    public QcService(
        QcRecordRepository qcRecordRepository,
        QcReferenceService qcReferenceService,
        WestgardEngine westgardEngine,
        QcExamRepository qcExamRepository,
        AuditService auditService,
        MeterRegistry meterRegistry,
        PostCalibrationRecordRepository postCalibrationRecordRepository
    ) {
        this.qcRecordRepository = qcRecordRepository;
        this.qcReferenceService = qcReferenceService;
        this.westgardEngine = westgardEngine;
        this.qcExamRepository = qcExamRepository;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
        this.postCalibrationRecordRepository = postCalibrationRecordRepository;
    }

    @Transactional
    public QcRecordResponse createRecord(QcRecordRequest request) {
        ensureExamExists(request.examName(), request.area());

        QcReferenceValue reference = resolveReference(request);
        QcRecord record = buildRecord(request, reference, null);
        List<QcRecord> history = loadWestgardHistory(record, null);
        applyCanonicalDecision(record, history);
        QcRecord saved = qcRecordRepository.save(record);
        Counter.builder("bioqc.qc.records.created")
            .description("Number of QC records created")
            .tag("area", saved.getArea() != null ? saved.getArea() : "unknown")
            .tag("status", saved.getStatus() != null ? saved.getStatus() : "unknown")
            .register(meterRegistry)
            .increment();
        auditService.log("CRIAR_REGISTRO_CQ", "QcRecord", saved.getId(),
            Map.of("exame", saved.getExamName(), "area", saved.getArea(), "valor", saved.getValue(), "status", saved.getStatus()));
        String referenceWarning = computeReferenceWarning(saved.getReference());
        return ResponseMapper.toQcRecordResponse(saved, referenceWarning);
    }

    @Transactional
    public List<QcRecordResponse> createRecordsBatch(List<QcRecordRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException("Nenhum registro foi enviado para importação.");
        }
        if (requests.size() > MAX_BATCH_SIZE) {
            throw new BusinessException("O lote excede o limite de 1000 registros por importação.");
        }

        List<String> failures = new ArrayList<>();
        List<QcRecord> validatedRecords = new ArrayList<>();

        for (int index = 0; index < requests.size(); index++) {
            QcRecordRequest request = requests.get(index);
            try {
                ensureExamExists(request.examName(), request.area());
                QcReferenceValue reference = resolveReference(request);
                QcRecord record = buildRecord(request, reference, null);
                List<QcRecord> history = loadWestgardHistory(record, null);
                applyCanonicalDecision(record, history);
                validatedRecords.add(record);
            } catch (BusinessException | ResourceNotFoundException exception) {
                failures.add("Linha " + (index + 1) + ": " + exception.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            throw new BusinessException(String.join("; ", failures));
        }

        return validatedRecords.stream()
            .map(qcRecordRepository::save)
            .map(ResponseMapper::toQcRecordResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<QcRecordResponse> getRecords(
        String area,
        String examName,
        LocalDate startDate,
        LocalDate endDate
    ) {
        List<QcRecord> records = qcRecordRepository.findByFilters(area, examName, startDate, endDate);
        Map<UUID, PostCalibrationRecord> postCalibrations = loadPostCalibrations(records);
        return records.stream()
            .map(record -> ResponseMapper.toQcRecordResponse(record, null, postCalibrations.get(record.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public QcRecordResponse getRecord(UUID id) {
        return qcRecordRepository.findById(id)
            .map(record -> ResponseMapper.toQcRecordResponse(
                record,
                null,
                postCalibrationRecordRepository.findByQcRecordId(record.getId()).orElse(null)
            ))
            .orElseThrow(() -> new ResourceNotFoundException("Registro de CQ não encontrado"));
    }

    private Map<UUID, PostCalibrationRecord> loadPostCalibrations(List<QcRecord> records) {
        if (records.isEmpty()) {
            return new HashMap<>();
        }
        List<UUID> ids = records.stream().map(QcRecord::getId).toList();
        return postCalibrationRecordRepository.findByQcRecord_IdIn(ids).stream()
            .collect(Collectors.toMap(
                post -> post.getQcRecord().getId(),
                Function.identity(),
                (left, right) -> left
            ));
    }

    @Transactional
    public QcRecordResponse updateRecord(UUID id, QcRecordRequest request) {
        QcRecord existing = qcRecordRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Registro de CQ não encontrado"));
        QcReferenceValue reference = resolveReference(request);
        QcRecord updated = buildRecord(request, reference, existing);
        List<QcRecord> history = loadWestgardHistory(updated, existing);
        applyCanonicalDecision(updated, history);
        return ResponseMapper.toQcRecordResponse(qcRecordRepository.save(updated));
    }

    @Transactional
    public void deleteRecord(UUID id) {
        if (!qcRecordRepository.existsById(id)) {
            throw new ResourceNotFoundException("Registro de CQ não encontrado");
        }
        qcRecordRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStatisticsToday() {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.now();
        LocalDate startMonth = currentMonth.atDay(1);
        LocalDate endMonth = currentMonth.atEndOfMonth();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalToday", qcRecordRepository.countByDate(today));
        response.put("totalMonth", qcRecordRepository.countByDateBetween(startMonth, endMonth));
        response.put("approvalRate", qcRecordRepository.calculateApprovalRate(startMonth, endMonth));
        return response;
    }

    @Transactional(readOnly = true)
    public List<LeveyJenningsResponse> getLeveyJenningsData(String examName, String level, String area, Integer days) {
        int effectiveDays = days != null && days > 0 ? days : 30;
        LocalDate cutoff = LocalDate.now().minusDays(effectiveDays - 1L);
        return qcRecordRepository.findLeveyJenningsData(examName, level, area, PageRequest.of(0, 500)).stream()
            .filter(record -> record.getDate() != null && !record.getDate().isBefore(cutoff))
            .sorted(Comparator.comparing(QcRecord::getDate))
            .map(record -> {
                double target = record.getTargetValue() != null ? record.getTargetValue() : 0.0;
                double sd = record.getTargetSd() != null ? record.getTargetSd() : 0.0;
                return new LeveyJenningsResponse(
                    record.getDate(),
                    record.getValue(),
                    target,
                    sd,
                    record.getCv(),
                    record.getStatus(),
                    record.getZScore(),
                    target + (2 * sd),
                    target - (2 * sd),
                    target + (3 * sd),
                    target - (3 * sd)
                );
            })
            .toList();
    }

    private void ensureExamExists(String examName, String area) {
        boolean exists = qcExamRepository.findByAreaAndIsActiveTrue(area).stream()
            .map(QcExam::getName)
            .anyMatch(name -> name.equalsIgnoreCase(examName));
        if (!exists) {
            throw new BusinessException("Exame não cadastrado para a área informada");
        }
    }

    private QcReferenceValue resolveReference(QcRecordRequest request) {
        return qcReferenceService.resolveApplicableReference(
            request.examName(),
            request.area(),
            request.level(),
            request.date(),
            request.lotNumber(),
            request.referenceId()
        );
    }

    private QcRecord buildRecord(QcRecordRequest request, QcReferenceValue reference, QcRecord existing) {
        double targetValue = reference != null ? reference.getTargetValue() : NumericUtils.defaultIfNull(request.targetValue());
        double targetSd = reference != null ? reference.getTargetSd() : NumericUtils.defaultIfNull(request.targetSd());
        double cvLimit = resolveCvLimit(request.cvLimit(), reference);
        String resolvedLotNumber = normalizeNullable(request.lotNumber());
        if (resolvedLotNumber == null && reference != null) {
            resolvedLotNumber = normalizeNullable(reference.getLotNumber());
        }

        QcRecord record = existing == null ? new QcRecord() : existing;
        record.setReference(reference);
        record.setExamName(request.examName());
        record.setArea(request.area());
        record.setDate(request.date());
        record.setLevel(request.level());
        record.setLotNumber(resolvedLotNumber);
        record.setValue(request.value());
        record.setTargetValue(targetValue);
        record.setTargetSd(targetSd);
        record.setCvLimit(cvLimit);
        record.setEquipment(request.equipment());
        record.setAnalyst(request.analyst());
        record.setViolations(new ArrayList<>());
        return record;
    }

    private String computeReferenceWarning(QcReferenceValue reference) {
        if (reference == null || reference.getValidUntil() == null) return null;
        long daysUntilExpiry = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), reference.getValidUntil());
        if (daysUntilExpiry >= 0 && daysUntilExpiry <= 7) {
            return "Referência vence em " + daysUntilExpiry + " dia" + (daysUntilExpiry != 1 ? "s" : "");
        }
        return null;
    }

    private void applyCanonicalDecision(QcRecord record, List<QcRecord> history) {
        record.setCv(NumericUtils.calculateCv(record.getValue(), record.getTargetValue()));
        record.setZScore(westgardEngine.calculateZScore(record));
        record.setNeedsCalibration(requiresCalibration(record));

        List<WestgardEngine.Violation> evaluation = westgardEngine.evaluate(record, history);
        List<WestgardViolation> violations = evaluation.stream()
            .map(result -> WestgardViolation.builder()
                .qcRecord(record)
                .rule(result.rule())
                .description(result.description())
                .severity(result.severity().name())
                .build())
            .toList();
        record.setViolations(new ArrayList<>(violations));
        record.setStatus(resolveStatus(evaluation));
    }

    private String resolveStatus(List<WestgardEngine.Violation> evaluation) {
        boolean hasRejection = evaluation.stream().anyMatch(v -> v.severity() == WestgardEngine.Severity.REJECTION);
        boolean hasWarning = evaluation.stream().anyMatch(v -> v.severity() == WestgardEngine.Severity.WARNING);
        if (hasRejection) {
            return "REPROVADO";
        }
        if (hasWarning) {
            return "ALERTA";
        }
        return "APROVADO";
    }

    private boolean requiresCalibration(QcRecord record) {
        return record.getCv() > NumericUtils.defaultIfNull(record.getCvLimit());
    }

    private double resolveCvLimit(Double requestCvLimit, QcReferenceValue reference) {
        if (requestCvLimit != null) {
            return requestCvLimit;
        }
        if (reference != null && reference.getCvMaxThreshold() != null) {
            return reference.getCvMaxThreshold();
        }
        return DEFAULT_CV_LIMIT;
    }

    private List<QcRecord> loadWestgardHistory(QcRecord record, QcRecord existing) {
        if (
            record.getReference() == null
                || record.getReference().getId() == null
                || record.getDate() == null
                || record.getExamName() == null
                || record.getLevel() == null
                || record.getArea() == null
        ) {
            return List.of();
        }
        String currentLotNumber = normalizeNullable(record.getLotNumber());
        Instant currentCreatedAt = existing != null ? existing.getCreatedAt() : null;

        return qcRecordRepository.findWestgardHistory(
            record.getReference().getId(),
            record.getExamName(),
            record.getLevel(),
            record.getArea(),
            record.getDate(),
            existing != null ? existing.getId() : null,
            PageRequest.of(0, WESTGARD_HISTORY_FETCH_SIZE)
        ).stream()
            .filter(candidate -> belongsToSameHistoryScope(candidate, record))
            .filter(candidate -> isWithinHistoricalBoundary(candidate, record.getDate(), currentCreatedAt))
            .filter(candidate -> currentLotNumber == null || sameNormalizedValue(candidate.getLotNumber(), currentLotNumber))
            .limit(WESTGARD_HISTORY_LIMIT)
            .toList();
    }

    private boolean belongsToSameHistoryScope(QcRecord candidate, QcRecord current) {
        if (candidate == null || candidate.getReference() == null || candidate.getReference().getId() == null) {
            return false;
        }
        return candidate.getReference().getId().equals(current.getReference().getId())
            && candidate.getExamName() != null
            && candidate.getExamName().equalsIgnoreCase(current.getExamName())
            && candidate.getLevel() != null
            && candidate.getLevel().equalsIgnoreCase(current.getLevel())
            && candidate.getArea() != null
            && candidate.getArea().equalsIgnoreCase(current.getArea());
    }

    private boolean isWithinHistoricalBoundary(QcRecord candidate, LocalDate currentDate, Instant currentCreatedAt) {
        if (candidate == null || candidate.getDate() == null || candidate.getDate().isAfter(currentDate)) {
            return false;
        }
        if (candidate.getDate().isBefore(currentDate) || currentCreatedAt == null) {
            return true;
        }
        Instant candidateCreatedAt = candidate.getCreatedAt();
        return candidateCreatedAt != null && candidateCreatedAt.isBefore(currentCreatedAt);
    }

    private boolean sameNormalizedValue(String value, String expected) {
        String normalized = normalizeNullable(value);
        return normalized != null && normalized.equalsIgnoreCase(expected);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
