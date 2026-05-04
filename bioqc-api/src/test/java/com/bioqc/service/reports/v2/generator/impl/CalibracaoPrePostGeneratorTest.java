package com.bioqc.service.reports.v2.generator.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bioqc.entity.PostCalibrationRecord;
import com.bioqc.entity.QcRecord;
import com.bioqc.repository.PostCalibrationRecordRepository;
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
class CalibracaoPrePostGeneratorTest {

    @Mock PostCalibrationRecordRepository repository;

    private CalibracaoPrePostGenerator generator() {
        return new CalibracaoPrePostGenerator(
            repository,
            GeneratorTestSupport.stubNumbering(),
            new JFreeChartRenderer(),
            new LabHeaderRenderer(),
            GeneratorTestSupport.stubLabSettings(),
            GeneratorTestSupport.stubAi("Analise IA Calibracao fixture"),
            new com.bioqc.config.ReportsV2Properties()
        );
    }

    private PostCalibrationRecord rec(String exam, Double origCv, Double postCv, LocalDate date) {
        QcRecord qc = QcRecord.builder()
            .id(UUID.randomUUID())
            .examName(exam)
            .area("bioquimica")
            .date(date)
            .lotNumber("L-1")
            .build();
        return PostCalibrationRecord.builder()
            .id(UUID.randomUUID())
            .qcRecord(qc)
            .examName(exam)
            .originalCv(origCv)
            .postCalibrationCv(postCv)
            .date(date)
            .build();
    }

    @Test
    @DisplayName("definition expoe CALIBRACAO_PREPOST")
    void definitionMetadata() {
        assertThat(generator().definition().code()).isEqualTo(ReportCode.CALIBRACAO_PREPOST);
    }

    @Test
    @DisplayName("generate produz PDF com eficazes, sem efeito e pioraram")
    void generateProducesPdf() {
        LocalDate today = LocalDate.now();
        List<PostCalibrationRecord> fixtures = List.of(
            rec("Glicose", 5.0, 3.0, today),       // EFICAZ
            rec("Ureia",   4.0, 4.0, today),       // SEM EFEITO
            rec("Colesterol", 3.0, 5.0, today)     // PIOROU
        );
        when(repository.findByQcRecordAreaAndDateRange(eq("bioquimica"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(fixtures);

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
    @DisplayName("generate com includeAiCommentary injeta IA")
    void generateWithAiCommentary() {
        when(repository.findByQcRecordAreaAndDateRange(eq("bioquimica"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());
        ReportArtifact artifact = generator().generate(
            new ReportFilters(Map.of("periodType", "current-month", "includeAiCommentary", true)),
            GeneratorTestSupport.ctx()
        );
        String text = GeneratorTestSupport.extractPdfText(artifact.bytes());
        assertThat(text).contains("Analise IA Calibracao fixture");
    }
}
