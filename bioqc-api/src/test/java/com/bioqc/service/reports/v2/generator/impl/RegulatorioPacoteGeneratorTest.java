package com.bioqc.service.reports.v2.generator.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bioqc.service.reports.v2.catalog.ReportCode;
import com.bioqc.service.reports.v2.generator.GenerationContext;
import com.bioqc.service.reports.v2.generator.ReportArtifact;
import com.bioqc.service.reports.v2.generator.ReportFilters;
import com.bioqc.service.reports.v2.generator.ReportPreview;
import com.bioqc.service.reports.v2.generator.pdf.LabHeaderRenderer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke + comportamento de falha parcial do pacote regulatorio (T1/T2).
 *
 * <p>Nao usamos Mockito para os generators subordinados porque sao classes
 * concretas (Mockito-inline tem problemas de mock em Java 25 com classes
 * dessa hierarquia). Em vez disso, fornecemos subclasses de teste minimas
 * que seguem o contrato {@code ReportGenerator}.
 *
 * <p>{@link SubordinateInvocation} tambem e um stub passthrough — como
 * {@code @Transactional(REQUIRES_NEW)} so funciona com proxy Spring, um
 * integration test seria necessario para validar rollback real. Aqui
 * validamos o comportamento do generator com o contrato publico do helper.
 */
class RegulatorioPacoteGeneratorTest {

    // ---------- fixtures de helper ----------

    /** Helper que emula SubordinateInvocation sem contexto Spring. */
    static SubordinateInvocation passthroughSubordinate() {
        return new SubordinateInvocation() {
            @Override
            public <T> Optional<T> runIsolated(String sectionName, Supplier<T> task) {
                try {
                    return Optional.ofNullable(task.get());
                } catch (RuntimeException ex) {
                    return Optional.empty();
                }
            }
        };
    }

    private static byte[] minimalPdfFixture() {
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            com.lowagie.text.Document doc = new com.lowagie.text.Document();
            com.lowagie.text.pdf.PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new com.lowagie.text.Paragraph("Fixture"));
            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static ReportArtifact stubArtifact() {
        byte[] pdf = minimalPdfFixture();
        return new ReportArtifact(pdf, "application/pdf", "x.pdf", 1, pdf.length,
            "BIO-202604-000001", "0".repeat(64), "Abril/2026");
    }

    // ---------- stubs de subordinados ----------

    /**
     * Base que implementa os metodos nao usados para evitar NPE. Cada teste
     * cria uma subclasse ad-hoc sobrescrevendo apenas {@code generate}.
     */
    static class NoopSubordinate {
        // tipo sem implementacao; apenas marca a intencao. As subclasses
        // concretas abaixo estendem diretamente as classes reais.
    }

    // Subclasse de CqOperationalV2Generator que nao chama super (fornecemos
    // apenas o comportamento de generate/preview por override). Evita criar
    // e passar ~12 dependencias que o construtor real exige.
    static CqOperationalV2Generator cqStub(final java.util.function.Supplier<ReportArtifact> behavior) {
        return new CqOperationalV2Generator(null, null, null, null, null, null, null, null, null, null, null, null, null) {
            @Override
            public ReportArtifact generate(ReportFilters filters, GenerationContext ctx) {
                return behavior.get();
            }
            @Override
            public ReportPreview preview(ReportFilters filters, GenerationContext ctx) {
                return new ReportPreview("", List.of(), "");
            }
        };
    }

    static WestgardDeepdiveGenerator westgardStub(final java.util.function.Supplier<ReportArtifact> behavior) {
        return new WestgardDeepdiveGenerator(null, null, null, null, null, null) {
            @Override
            public ReportArtifact generate(ReportFilters filters, GenerationContext ctx) {
                return behavior.get();
            }
            @Override
            public ReportPreview preview(ReportFilters filters, GenerationContext ctx) {
                return new ReportPreview("", List.of(), "");
            }
        };
    }

    static ReagentesRastreabilidadeGenerator reagStub(final java.util.function.Supplier<ReportArtifact> behavior) {
        return new ReagentesRastreabilidadeGenerator(null, null, null, null, null, null, null, null) {
            @Override
            public ReportArtifact generate(ReportFilters filters, GenerationContext ctx) {
                return behavior.get();
            }
            @Override
            public ReportPreview preview(ReportFilters filters, GenerationContext ctx) {
                return new ReportPreview("", List.of(), "");
            }
        };
    }

    static ManutencaoKpiGenerator manutStub(final java.util.function.Supplier<ReportArtifact> behavior) {
        return new ManutencaoKpiGenerator(null, null, null, null, null, null) {
            @Override
            public ReportArtifact generate(ReportFilters filters, GenerationContext ctx) {
                return behavior.get();
            }
            @Override
            public ReportPreview preview(ReportFilters filters, GenerationContext ctx) {
                return new ReportPreview("", List.of(), "");
            }
        };
    }

    static CalibracaoPrePostGenerator calibStub(final java.util.function.Supplier<ReportArtifact> behavior) {
        return new CalibracaoPrePostGenerator(null, null, null, null, null, null, null) {
            @Override
            public ReportArtifact generate(ReportFilters filters, GenerationContext ctx) {
                return behavior.get();
            }
            @Override
            public ReportPreview preview(ReportFilters filters, GenerationContext ctx) {
                return new ReportPreview("", List.of(), "");
            }
        };
    }

    static MultiAreaConsolidadoGenerator multiStub(final java.util.function.Supplier<ReportArtifact> behavior) {
        return new MultiAreaConsolidadoGenerator(null, null, null, null, null, null, null, null, null) {
            @Override
            public ReportArtifact generate(ReportFilters filters, GenerationContext ctx) {
                return behavior.get();
            }
            @Override
            public ReportPreview preview(ReportFilters filters, GenerationContext ctx) {
                return new ReportPreview("", List.of(), "");
            }
        };
    }

    private RegulatorioPacoteGenerator buildGenerator(
        java.util.function.Supplier<ReportArtifact> cqBehavior,
        java.util.function.Supplier<ReportArtifact> westBehavior,
        java.util.function.Supplier<ReportArtifact> reagBehavior,
        java.util.function.Supplier<ReportArtifact> manutBehavior,
        java.util.function.Supplier<ReportArtifact> calibBehavior,
        java.util.function.Supplier<ReportArtifact> multiBehavior
    ) {
        return new RegulatorioPacoteGenerator(
            cqStub(cqBehavior),
            westgardStub(westBehavior),
            reagStub(reagBehavior),
            manutStub(manutBehavior),
            calibStub(calibBehavior),
            multiStub(multiBehavior),
            GeneratorTestSupport.stubNumbering(),
            new LabHeaderRenderer(),
            GeneratorTestSupport.stubLabSettings(),
            passthroughSubordinate()
        );
    }

    // ---------- testes ----------

    @Test
    @DisplayName("definition expoe REGULATORIO_PACOTE")
    void definitionMetadata() {
        RegulatorioPacoteGenerator gen = buildGenerator(
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact
        );
        assertThat(gen.definition().code()).isEqualTo(ReportCode.REGULATORIO_PACOTE);
    }

    @Test
    @DisplayName("generate — todas 6 secoes OK: zero warnings, pacote valido")
    void generateAllSectionsOk() {
        RegulatorioPacoteGenerator gen = buildGenerator(
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact
        );

        ReportArtifact result = gen.generate(
            new ReportFilters(Map.of("periodType", "current-month")),
            GeneratorTestSupport.ctx()
        );

        GeneratorTestSupport.assertPdfMagicHeader(result.bytes());
        assertThat(result.warnings()).isEmpty();
        assertThat(result.reportNumber()).matches("BIO-\\d{6}-\\d{6}");
    }

    @Test
    @DisplayName("T1/T2 — 1 subordinado falha, 5 geram: warning presente e pacote emitido")
    void generatePartialFailurePreservesOthers() {
        RegulatorioPacoteGenerator gen = buildGenerator(
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact,
            () -> { throw new RuntimeException("bug de prod — function lower(bytea) does not exist"); },
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact
        );

        ReportArtifact result = gen.generate(
            new ReportFilters(Map.of("periodType", "current-month")),
            GeneratorTestSupport.ctx()
        );

        GeneratorTestSupport.assertPdfMagicHeader(result.bytes());
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("KPIs de Manutencao");
        // A declaracao de autenticidade deve exibir a caixa de atencao.
        String text = GeneratorTestSupport.extractPdfText(result.bytes());
        assertThat(text).containsIgnoringCase("ATENCAO");
        assertThat(text).contains("KPIs de Manutencao");
    }

    @Test
    @DisplayName("generate aborta quando todas 6 secoes falham")
    void generateAbortsWhenAllFail() {
        Supplier<ReportArtifact> failing = () -> { throw new RuntimeException("boom"); };
        RegulatorioPacoteGenerator gen = buildGenerator(
            failing, failing, failing, failing, failing, failing
        );
        assertThatThrownBy(() -> gen.generate(
            new ReportFilters(Map.of("periodType", "current-month")),
            GeneratorTestSupport.ctx()
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("todas as 6 secoes falharam");
    }

    @Test
    @DisplayName("preview retorna HTML leve com indice")
    void previewMetadata() {
        RegulatorioPacoteGenerator gen = buildGenerator(
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact,
            RegulatorioPacoteGeneratorTest::stubArtifact
        );
        var preview = gen.preview(
            new ReportFilters(Map.of("periodType", "current-month")),
            GeneratorTestSupport.ctx()
        );
        assertThat(preview.html()).contains("Pacote Regulatorio");
        assertThat(preview.warnings()).isEmpty();
    }
}
