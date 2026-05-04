package com.bioqc.service.reports.v2.generator.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.bioqc.entity.QcRecord;
import com.bioqc.repository.MaintenanceRecordRepository;
import com.bioqc.repository.QcRecordRepository;
import com.bioqc.repository.ReagentLotRepository;
import com.bioqc.repository.WestgardViolationRepository;
import com.bioqc.service.reports.v2.catalog.ReportCode;
import com.bioqc.service.reports.v2.generator.ReportArtifact;
import com.bioqc.service.reports.v2.generator.ReportFilters;
import com.bioqc.service.reports.v2.generator.chart.JFreeChartRenderer;
import com.bioqc.service.reports.v2.generator.pdf.LabHeaderRenderer;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MultiAreaConsolidadoGeneratorTest {

    @Mock QcRecordRepository qcRecordRepository;
    @Mock WestgardViolationRepository westgardRepository;
    @Mock ReagentLotRepository reagentLotRepository;
    @Mock MaintenanceRecordRepository maintenanceRepository;

    private MultiAreaConsolidadoGenerator generator() {
        return new MultiAreaConsolidadoGenerator(
            qcRecordRepository,
            westgardRepository,
            reagentLotRepository,
            maintenanceRepository,
            GeneratorTestSupport.stubNumbering(),
            new JFreeChartRenderer(),
            new LabHeaderRenderer(),
            GeneratorTestSupport.stubLabSettings(),
            GeneratorTestSupport.stubAi("Analise IA Consolidado fixture")
        );
    }

    private QcRecord qc(String area, String status, LocalDate date) {
        return QcRecord.builder()
            .id(UUID.randomUUID())
            .examName("Exame " + area)
            .area(area)
            .date(date)
            .level("N1")
            .value(100.0)
            .targetValue(100.0)
            .status(status)
            .build();
    }

    @Test
    @DisplayName("definition expoe MULTI_AREA_CONSOLIDADO")
    void definitionMetadata() {
        assertThat(generator().definition().code()).isEqualTo(ReportCode.MULTI_AREA_CONSOLIDADO);
    }

    @Test
    @DisplayName("generate produz PDF com 3 areas e fixtures variadas")
    void generateProducesPdf() {
        LocalDate today = LocalDate.now();
        lenient().when(qcRecordRepository.findByAreaAndDateRange(anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(qc("bioquimica", "APROVADO", today), qc("bioquimica", "ALERTA", today)));
        lenient().when(reagentLotRepository.countExpiredWithStock(any(LocalDate.class))).thenReturn(2L);
        lenient().when(maintenanceRepository.findOverdue(any(LocalDate.class))).thenReturn(List.of());
        lenient().when(westgardRepository.findByAreaAndPeriod(isNull(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        ReportArtifact artifact = generator().generate(
            new ReportFilters(Map.of(
                "areas", List.of("bioquimica", "hematologia"),
                "periodType", "current-month"
            )),
            GeneratorTestSupport.ctx()
        );

        GeneratorTestSupport.assertPdfMagicHeader(artifact.bytes());
        assertThat(artifact.sha256()).hasSize(64);
        assertThat(artifact.reportNumber()).matches("BIO-\\d{6}-\\d{6}");

        String text = GeneratorTestSupport.extractPdfText(artifact.bytes());
        assertThat(text).containsIgnoringCase("Consolidado");
    }

    @Test
    @DisplayName("generate com includeAiCommentary injeta IA")
    void generateWithAiCommentary() {
        lenient().when(qcRecordRepository.findByAreaAndDateRange(anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());
        lenient().when(reagentLotRepository.countExpiredWithStock(any(LocalDate.class))).thenReturn(0L);
        lenient().when(maintenanceRepository.findOverdue(any(LocalDate.class))).thenReturn(List.of());
        lenient().when(westgardRepository.findByAreaAndPeriod(isNull(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());
        ReportArtifact artifact = generator().generate(
            new ReportFilters(Map.of("periodType", "current-month", "includeAiCommentary", true)),
            GeneratorTestSupport.ctx()
        );
        String text = GeneratorTestSupport.extractPdfText(artifact.bytes());
        assertThat(text).contains("Analise IA Consolidado fixture");
    }
}
