package com.bioqc.service;

import com.bioqc.dto.response.DashboardAlertsResponse;
import com.bioqc.dto.response.DashboardKpiResponse;
import com.bioqc.dto.response.MaintenanceResponse;
import com.bioqc.dto.response.QcRecordResponse;
import com.bioqc.dto.response.ReagentLotResponse;
import com.bioqc.entity.QcRecord;
import com.bioqc.entity.WestgardViolation;
import com.bioqc.repository.MaintenanceRecordRepository;
import com.bioqc.repository.QcRecordRepository;
import com.bioqc.repository.ReagentLotRepository;
import com.bioqc.repository.WestgardViolationRepository;
import com.bioqc.util.ResponseMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final QcRecordRepository qcRecordRepository;
    private final ReagentLotRepository reagentLotRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final WestgardViolationRepository westgardViolationRepository;

    public DashboardService(
        QcRecordRepository qcRecordRepository,
        ReagentLotRepository reagentLotRepository,
        MaintenanceRecordRepository maintenanceRecordRepository,
        WestgardViolationRepository westgardViolationRepository
    ) {
        this.qcRecordRepository = qcRecordRepository;
        this.reagentLotRepository = reagentLotRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.westgardViolationRepository = westgardViolationRepository;
    }

    @Transactional(readOnly = true)
    public DashboardKpiResponse getKpis(String area) {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.now();
        LocalDate startMonth = currentMonth.atDay(1);
        LocalDate endMonth = currentMonth.atEndOfMonth();

        long totalToday;
        long totalMonth;
        Double rate;
        if (area != null && !area.isBlank()) {
            totalToday = qcRecordRepository.countByDateAndArea(today, area);
            totalMonth = qcRecordRepository.countByDateBetweenAndArea(startMonth, endMonth, area);
            rate = qcRecordRepository.calculateApprovalRateByArea(startMonth, endMonth, area);
        } else {
            totalToday = qcRecordRepository.countByDate(today);
            totalMonth = qcRecordRepository.countByDateBetween(startMonth, endMonth);
            rate = qcRecordRepository.calculateApprovalRate(startMonth, endMonth);
        }
        double approvalRate = rate != null ? rate : 0.0;
        long alertsCount = getAlertsCount();

        return new DashboardKpiResponse(
            totalToday,
            totalMonth,
            approvalRate,
            alertsCount > 0,
            alertsCount
        );
    }

    @Transactional(readOnly = true)
    public DashboardAlertsResponse getAlerts() {
        List<ReagentLotResponse> expiringReagents = reagentLotRepository
            .findExpiringLots(LocalDate.now(), LocalDate.now().plusDays(30))
            .stream()
            .map(ResponseMapper::toReagentLotResponse)
            .toList();

        List<MaintenanceResponse> pendingMaintenances = maintenanceRecordRepository.findPendingMaintenances()
            .stream()
            .map(ResponseMapper::toMaintenanceResponse)
            .toList();
        List<QcRecordResponse> westgardViolations = mapRejectedRecordsOfCurrentMonth();

        return new DashboardAlertsResponse(
            new DashboardAlertsResponse.AlertSection<>(expiringReagents.size(), expiringReagents),
            new DashboardAlertsResponse.AlertSection<>(pendingMaintenances.size(), pendingMaintenances),
            new DashboardAlertsResponse.AlertSection<>(westgardViolations.size(), westgardViolations)
        );
    }

    @Transactional(readOnly = true)
    public List<QcRecordResponse> getRecentRecords(int limit) {
        return qcRecordRepository.findAll(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
            ).getContent().stream()
            .map(ResponseMapper::toQcRecordResponse)
            .toList();
    }

    private long getAlertsCount() {
        Instant startOfMonth = YearMonth.now().atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long expiring = reagentLotRepository.countExpiringLots(LocalDate.now(), LocalDate.now().plusDays(30));
        long pendingMaintenances = maintenanceRecordRepository.countPendingMaintenances();
        long rejected = westgardViolationRepository.countDistinctRejectedRecords(startOfMonth);
        return expiring + pendingMaintenances + rejected;
    }

    private List<QcRecordResponse> mapRejectedRecordsOfCurrentMonth() {
        Instant startOfMonth = YearMonth.now().atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<WestgardViolation> violations = westgardViolationRepository.findRecentRejections(startOfMonth);
        Map<UUID, QcRecord> uniqueRecords = new LinkedHashMap<>();
        for (WestgardViolation violation : violations) {
            if (violation.getQcRecord() != null) {
                uniqueRecords.putIfAbsent(violation.getQcRecord().getId(), violation.getQcRecord());
            }
        }
        List<QcRecordResponse> responses = new ArrayList<>();
        uniqueRecords.values().forEach(record -> responses.add(ResponseMapper.toQcRecordResponse(record)));
        return responses;
    }
}
