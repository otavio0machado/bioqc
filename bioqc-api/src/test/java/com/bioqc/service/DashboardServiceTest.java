package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bioqc.dto.response.DashboardAlertsResponse;
import com.bioqc.dto.response.DashboardKpiResponse;
import com.bioqc.dto.response.QcRecordResponse;
import com.bioqc.entity.MaintenanceRecord;
import com.bioqc.entity.QcRecord;
import com.bioqc.entity.ReagentLot;
import com.bioqc.entity.WestgardViolation;
import com.bioqc.repository.MaintenanceRecordRepository;
import com.bioqc.repository.QcRecordRepository;
import com.bioqc.repository.ReagentLotRepository;
import com.bioqc.repository.WestgardViolationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @InjectMocks
    private DashboardService dashboardService;

    @Mock
    private QcRecordRepository qcRecordRepository;

    @Mock
    private ReagentLotRepository reagentLotRepository;

    @Mock
    private MaintenanceRecordRepository maintenanceRecordRepository;

    @Mock
    private WestgardViolationRepository westgardViolationRepository;

    @Test
    @DisplayName("getKpis com registros retorna contagens corretas")
    void getKpis_comRegistros_retornaContagensCorretas() {
        when(qcRecordRepository.countByDate(any(LocalDate.class))).thenReturn(5L);
        when(qcRecordRepository.countByDateBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(42L);
        when(qcRecordRepository.calculateApprovalRate(any(LocalDate.class), any(LocalDate.class))).thenReturn(85.7);
        when(reagentLotRepository.countExpiringLots(any(LocalDate.class), any(LocalDate.class))).thenReturn(1L);
        when(maintenanceRecordRepository.countPendingMaintenances()).thenReturn(1L);
        when(westgardViolationRepository.countDistinctRejectedRecords(any(Instant.class))).thenReturn(0L);

        DashboardKpiResponse kpis = dashboardService.getKpis(null);

        assertThat(kpis.totalToday()).isEqualTo(5L);
        assertThat(kpis.totalMonth()).isEqualTo(42L);
        assertThat(kpis.approvalRate()).isEqualTo(85.7);
        assertThat(kpis.hasAlerts()).isTrue();
        assertThat(kpis.alertsCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getKpis sem registros retorna zeros")
    void getKpis_semRegistros_retornaZeros() {
        when(qcRecordRepository.countByDate(any(LocalDate.class))).thenReturn(0L);
        when(qcRecordRepository.countByDateBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(0L);
        when(qcRecordRepository.calculateApprovalRate(any(LocalDate.class), any(LocalDate.class))).thenReturn(null);
        when(reagentLotRepository.countExpiringLots(any(LocalDate.class), any(LocalDate.class))).thenReturn(0L);
        when(maintenanceRecordRepository.countPendingMaintenances()).thenReturn(0L);
        when(westgardViolationRepository.countDistinctRejectedRecords(any(Instant.class))).thenReturn(0L);

        DashboardKpiResponse kpis = dashboardService.getKpis(null);

        assertThat(kpis.totalToday()).isZero();
        assertThat(kpis.totalMonth()).isZero();
        assertThat(kpis.approvalRate()).isEqualTo(0.0);
        assertThat(kpis.hasAlerts()).isFalse();
        assertThat(kpis.alertsCount()).isZero();
    }

    @Test
    @DisplayName("getAlerts retorna alertas de reagentes e manutencao")
    void getAlerts_retornaAlertasDeReagentesEManutencao() {
        ReagentLot lot = reagentLot();
        MaintenanceRecord maintenance = maintenanceRecord();
        when(reagentLotRepository.findExpiringLots(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(lot));
        when(maintenanceRecordRepository.findPendingMaintenances()).thenReturn(List.of(maintenance));
        when(westgardViolationRepository.findRecentRejections(any(Instant.class))).thenReturn(List.of());

        DashboardAlertsResponse alerts = dashboardService.getAlerts();

        assertThat(alerts.expiringReagents().count()).isEqualTo(1);
        assertThat(alerts.expiringReagents().items()).hasSize(1);
        assertThat(alerts.pendingMaintenances().count()).isEqualTo(1);
        assertThat(alerts.pendingMaintenances().items()).hasSize(1);
        assertThat(alerts.westgardViolations().count()).isZero();
    }

    @Test
    @DisplayName("getRecentRecords retorna registros limitados")
    void getRecentRecords_retornaRegistrosLimitados() {
        QcRecord record = qcRecord();
        Page<QcRecord> page = new PageImpl<>(List.of(record));
        when(qcRecordRepository.findAll(any(Pageable.class))).thenReturn(page);

        List<QcRecordResponse> records = dashboardService.getRecentRecords(5);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).examName()).isEqualTo("Glicose");
    }

    // --- Helpers ---

    private static QcRecord qcRecord() {
        return QcRecord.builder()
            .id(UUID.randomUUID())
            .examName("Glicose")
            .area("bioquimica")
            .date(LocalDate.now())
            .level("N1")
            .value(100.0)
            .targetValue(98.0)
            .targetSd(2.0)
            .status("APROVADO")
            .needsCalibration(false)
            .violations(List.of())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private static ReagentLot reagentLot() {
        return ReagentLot.builder()
            .id(UUID.randomUUID())
            .name("ALT")
            .lotNumber("L001")
            .manufacturer("Bio")
            .category("Bioquimica")
            .expiryDate(LocalDate.now().plusDays(10))
            .unitsInStock(80)
            .unitsInUse(0)
            .status("em_estoque")
            .needsStockReview(false)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private static MaintenanceRecord maintenanceRecord() {
        return MaintenanceRecord.builder()
            .id(UUID.randomUUID())
            .equipment("Espectrofotometro")
            .type("Preventiva")
            .date(LocalDate.now().minusDays(30))
            .nextDate(LocalDate.now().minusDays(1))
            .technician("Carlos")
            .notes("Verificacao de rotina")
            .createdAt(Instant.now())
            .build();
    }
}
