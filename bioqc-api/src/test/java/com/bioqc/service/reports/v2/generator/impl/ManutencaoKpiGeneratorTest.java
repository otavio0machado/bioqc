package com.bioqc.service.reports.v2.generator.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.bioqc.entity.MaintenanceRecord;
import com.bioqc.repository.MaintenanceRecordRepository;
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
class ManutencaoKpiGeneratorTest {

    @Mock MaintenanceRecordRepository repository;

    private ManutencaoKpiGenerator generator() {
        return new ManutencaoKpiGenerator(
            repository,
            GeneratorTestSupport.stubNumbering(),
            new JFreeChartRenderer(),
            new LabHeaderRenderer(),
            GeneratorTestSupport.stubLabSettings(),
            GeneratorTestSupport.stubAi("Analise IA Manutencao fixture")
        );
    }

    private MaintenanceRecord rec(String equip, String type, LocalDate date, LocalDate next) {
        return MaintenanceRecord.builder()
            .id(UUID.randomUUID())
            .equipment(equip)
            .type(type)
            .date(date)
            .nextDate(next)
            .technician("Tecnico")
            .build();
    }

    @Test
    @DisplayName("definition expoe MANUTENCAO_KPI")
    void definitionMetadata() {
        assertThat(generator().definition().code()).isEqualTo(ReportCode.MANUTENCAO_KPI);
    }

    @Test
    @DisplayName("generate produz PDF valido com preventivas/corretivas/proximas")
    void generateProducesPdf() {
        LocalDate today = LocalDate.now();
        List<MaintenanceRecord> inPeriod = List.of(
            rec("Analisador A", "Preventiva", today.minusDays(5), today.plusDays(30)),
            rec("Analisador A", "Corretiva", today.minusDays(10), null),
            rec("Centrifuga B", "Preventiva", today.minusDays(20), today.plusDays(60))
        );
        when(repository.findInPeriod(any(LocalDate.class), any(LocalDate.class), isNull(), isNull()))
            .thenReturn(inPeriod);
        when(repository.findUpcoming(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(rec("Analisador A", "Preventiva", today.minusDays(5), today.plusDays(30))));
        when(repository.findOverdue(any(LocalDate.class))).thenReturn(List.of());

        ReportArtifact artifact = generator().generate(
            new ReportFilters(Map.of("periodType", "current-month")),
            GeneratorTestSupport.ctx()
        );

        GeneratorTestSupport.assertPdfMagicHeader(artifact.bytes());
        assertThat(artifact.sha256()).hasSize(64);
        assertThat(artifact.reportNumber()).matches("BIO-\\d{6}-\\d{6}");

        String text = GeneratorTestSupport.extractPdfText(artifact.bytes());
        assertThat(text).containsIgnoringCase("Resumo");
    }

    @Test
    @DisplayName("generate com includeAiCommentary injeta IA no PDF")
    void generateWithAiCommentary() {
        when(repository.findInPeriod(any(LocalDate.class), any(LocalDate.class), isNull(), isNull()))
            .thenReturn(List.of());
        when(repository.findUpcoming(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
        when(repository.findOverdue(any(LocalDate.class))).thenReturn(List.of());
        ReportArtifact artifact = generator().generate(
            new ReportFilters(Map.of("periodType", "current-month", "includeAiCommentary", true)),
            GeneratorTestSupport.ctx()
        );
        String text = GeneratorTestSupport.extractPdfText(artifact.bytes());
        assertThat(text).contains("Analise IA Manutencao fixture");
    }
}
