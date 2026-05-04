package com.bioqc.service.reports.v2.generator.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bioqc.entity.ReagentLot;
import com.bioqc.entity.ReagentStatus;
import com.bioqc.repository.ReagentLotRepository;
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
class ReagentesRastreabilidadeGeneratorTest {

    @Mock ReagentLotRepository lotRepository;
    @Mock com.bioqc.repository.StockMovementRepository movementRepository;
    @Mock com.bioqc.repository.QcRecordRepository qcRecordRepository;

    private ReagentesRastreabilidadeGenerator generator() {
        // Stubs default: lote sem movimentos, sem usos em CQ. Generators robustos a vazios.
        org.mockito.Mockito.lenient().when(movementRepository.findByReagentLotIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.List.of());
        org.mockito.Mockito.lenient().when(qcRecordRepository.findAll())
            .thenReturn(java.util.List.of());
        return new ReagentesRastreabilidadeGenerator(
            lotRepository,
            movementRepository,
            qcRecordRepository,
            GeneratorTestSupport.stubNumbering(),
            new JFreeChartRenderer(),
            new LabHeaderRenderer(),
            GeneratorTestSupport.stubLabSettings(),
            GeneratorTestSupport.stubAi("Analise IA Reagentes fixture")
        );
    }

    private ReagentLot lot(String name, String status, LocalDate expiry, Double stock, String category) {
        ReagentLot l = new ReagentLot();
        l.setId(UUID.randomUUID());
        l.setName(name);
        l.setLotNumber("L-" + name);
        l.setManufacturer("Bio");
        l.setCategory(category);
        l.setStatus(status);
        l.setExpiryDate(expiry);
        // v3: estoque per-unit. Fixture preserva total via unitsInStock; unitsInUse=0.
        l.setUnitsInStock(stock == null ? 0 : stock.intValue());
        l.setUnitsInUse(0);
        l.setNeedsStockReview(false);
        return l;
    }

    @Test
    @DisplayName("definition expoe REAGENTES_RASTREABILIDADE")
    void definitionMetadata() {
        assertThat(generator().definition().code()).isEqualTo(ReportCode.REAGENTES_RASTREABILIDADE);
    }

    @Test
    @DisplayName("generate produz PDF valido com lotes em status novo")
    void generateProducesPdf() {
        LocalDate today = LocalDate.now();
        List<ReagentLot> fixtures = List.of(
            lot("Reagente Glicose", ReagentStatus.EM_ESTOQUE, today.plusDays(30), 50D, "Bioquimica"),
            lot("Reagente Ureia", ReagentStatus.VENCIDO, today.minusDays(5), 10D, "Bioquimica"),
            lot("Reagente Hemoglobina", ReagentStatus.EM_USO, today.plusDays(180), 100D, "Hematologia"),
            lot("Reagente Microbiologia", ReagentStatus.INATIVO, today.plusDays(60), 0D, "Microbiologia")
        );
        when(lotRepository.findAll()).thenReturn(fixtures);

        ReportArtifact artifact = generator().generate(
            new ReportFilters(Map.of("includeInactive", true)),
            GeneratorTestSupport.ctx()
        );

        GeneratorTestSupport.assertPdfMagicHeader(artifact.bytes());
        assertThat(artifact.sha256()).hasSize(64);
        assertThat(artifact.reportNumber()).matches("BIO-\\d{6}-\\d{6}");
        assertThat(artifact.sizeBytes()).isGreaterThan(0);

        String text = GeneratorTestSupport.extractPdfText(artifact.bytes());
        // Cards de resumo apos refator-v3: Em estoque, Em uso, Inativos, Vencidos.
        assertThat(text).containsIgnoringCase("Resumo");
        assertThat(text).contains("Em estoque");
        assertThat(text).contains("Em uso");
        assertThat(text).contains("Inativos");
        // v3: card "Fora de estoque" removido (substituido por Inativos).
        assertThat(text).doesNotContain("Fora de estoque");
        // Nao deve ter secao de consumo (removida em refator-v2 §1.11).
        assertThat(text).doesNotContain("Consumo estimado por categoria");
    }

    @Test
    @DisplayName("PDF mantem secao 'Vencidos com estoque' quando ha lote nessa condicao")
    void generateMantemVencidosComEstoque() {
        LocalDate today = LocalDate.now();
        when(lotRepository.findAll()).thenReturn(List.of(
            lot("Reagente Vencido", ReagentStatus.VENCIDO, today.minusDays(3), 20D, "Bioquimica")
        ));

        ReportArtifact artifact = generator().generate(
            new ReportFilters(Map.of()),
            GeneratorTestSupport.ctx()
        );

        String text = GeneratorTestSupport.extractPdfText(artifact.bytes());
        assertThat(text).containsIgnoringCase("Vencidos com estoque");
    }

    @Test
    @DisplayName("generate com includeAiCommentary injeta string da IA no PDF")
    void generateWithAiCommentary() {
        LocalDate today = LocalDate.now();
        when(lotRepository.findAll()).thenReturn(List.of(
            lot("Reagente A", ReagentStatus.EM_ESTOQUE, today.plusDays(30), 50D, "Bioquimica")
        ));
        ReportArtifact artifact = generator().generate(
            new ReportFilters(Map.of("includeAiCommentary", true)),
            GeneratorTestSupport.ctx()
        );
        String text = GeneratorTestSupport.extractPdfText(artifact.bytes());
        assertThat(text).contains("Analise IA Reagentes fixture");
    }
}
