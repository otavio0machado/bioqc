package com.bioqc.service.reports.v2.generator.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bioqc.entity.QcRecord;
import com.bioqc.entity.WestgardViolation;
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
class WestgardDeepdiveGeneratorTest {

    @Mock WestgardViolationRepository violationRepository;

    private WestgardDeepdiveGenerator generator() {
        return new WestgardDeepdiveGenerator(
            violationRepository,
            GeneratorTestSupport.stubNumbering(),
            new JFreeChartRenderer(),
            new LabHeaderRenderer(),
            GeneratorTestSupport.stubLabSettings(),
            GeneratorTestSupport.stubAi("Analise IA Westgard fixture")
        );
    }

    private WestgardViolation fixture(String rule, String severity, String exam, LocalDate date) {
        QcRecord qc = QcRecord.builder()
            .id(UUID.randomUUID())
            .examName(exam)
            .area("bioquimica")
            .date(date)
            .level("N1")
            .lotNumber("L-123")
            .value(100.0)
            .targetValue(100.0)
            .status("REPROVADO")
            .build();
        return WestgardViolation.builder()
            .id(UUID.randomUUID())
            .qcRecord(qc)
            .rule(rule)
            .description("Descricao " + rule)
            .severity(severity)
            .build();
    }

    @Test
    @DisplayName("definition expoe WESTGARD_DEEPDIVE")
    void definitionMetadata() {
        assertThat(generator().definition().code()).isEqualTo(ReportCode.WESTGARD_DEEPDIVE);
    }

    @Test
    @DisplayName("generate produz PDF valido com 3 fixtures variadas")
    void generateProducesPdf() {
        LocalDate today = LocalDate.now();
        List<WestgardViolation> fixtures = List.of(
            fixture("1-3s", "REJEICAO", "Glicose", today),
            fixture("2-2s", "ADVERTENCIA", "Colesterol", today.minusDays(3)),
            fixture("R-4s", "REJEICAO", "Ureia", today.minusDays(7))
        );
        when(violationRepository.findByAreaAndPeriod(eq("bioquimica"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(fixtures);

        ReportArtifact artifact = generator().generate(
            new ReportFilters(Map.of("area", "bioquimica", "periodType", "current-month")),
            GeneratorTestSupport.ctx()
        );

        GeneratorTestSupport.assertPdfMagicHeader(artifact.bytes());
        assertThat(artifact.sha256()).hasSize(64);
        assertThat(artifact.sizeBytes()).isGreaterThan(0);
        assertThat(artifact.reportNumber()).matches("BIO-\\d{6}-\\d{6}");

        String text = GeneratorTestSupport.extractPdfText(artifact.bytes());
        assertThat(text).containsIgnoringCase("Resumo");
        assertThat(text).contains("1-3s");
    }

    @Test
    @DisplayName("generate com includeAiCommentary injeta string da IA no PDF")
    void generateWithAiCommentary() {
        when(violationRepository.findByAreaAndPeriod(eq("bioquimica"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(fixture("1-3s", "REJEICAO", "Glicose", LocalDate.now())));

        ReportArtifact artifact = generator().generate(
            new ReportFilters(Map.of(
                "area", "bioquimica",
                "periodType", "current-month",
                "includeAiCommentary", true
            )),
            GeneratorTestSupport.ctx()
        );
        String text = GeneratorTestSupport.extractPdfText(artifact.bytes());
        assertThat(text).contains("Analise IA Westgard fixture");
    }

    @Test
    @DisplayName("generate com zero violacoes ainda emite PDF valido")
    void generateWithEmptyFixtures() {
        when(violationRepository.findByAreaAndPeriod(eq("bioquimica"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());
        ReportArtifact artifact = generator().generate(
            new ReportFilters(Map.of("area", "bioquimica", "periodType", "current-month")),
            GeneratorTestSupport.ctx()
        );
        GeneratorTestSupport.assertPdfMagicHeader(artifact.bytes());
        assertThat(artifact.sha256()).hasSize(64);
    }
}
