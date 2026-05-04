package com.bioqc.service.reports.v2.generator.impl;

import com.bioqc.entity.LabSettings;
import com.bioqc.service.LabSettingsService;
import com.bioqc.service.ReportNumberingService;
import com.bioqc.service.reports.v2.catalog.ReportDefinition;
import com.bioqc.service.reports.v2.catalog.ReportDefinitionRegistry;
import com.bioqc.service.reports.v2.generator.GenerationContext;
import com.bioqc.service.reports.v2.generator.ReportArtifact;
import com.bioqc.service.reports.v2.generator.ReportFilters;
import com.bioqc.service.reports.v2.generator.ReportGenerator;
import com.bioqc.service.reports.v2.generator.ReportPreview;
import com.bioqc.service.reports.v2.generator.pdf.LabHeaderRenderer;
import com.bioqc.service.reports.v2.generator.pdf.PdfFooterRenderer;
import com.bioqc.service.reports.v2.generator.pdf.ReportV2PdfTheme;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestrador do pacote regulatorio. Gera, em sequencia, os 6 generators
 * anteriores e concatena os PDFs em um unico documento com:
 * - Declaracao de autenticidade (prepend)
 * - Indice textual
 * - Cada subordinado como secao
 *
 * <p><strong>Isolamento de transacao (T1)</strong>: cada generator subordinado
 * roda em transacao separada via {@link SubordinateInvocation} (propagacao
 * REQUIRES_NEW). Motivacao: quando uma secao falha com SQLException, a tx
 * outer entra em {@code current transaction is aborted, commands ignored} e
 * toda geracao do pacote morre em cascata. Com REQUIRES_NEW, cada secao tem
 * seu proprio escopo transacional — falhas ficam contidas.
 *
 * <p><strong>Falha parcial visivel (T2)</strong>: quando menos de 6 secoes
 * geram, o pacote e emitido mesmo assim. Na declaracao de autenticidade
 * imprimimos uma caixa de atencao listando quais secoes falharam; o
 * {@link ReportArtifact#warnings()} exposto no response carrega a mesma lista
 * para que o frontend mostre toast/banner. Apenas quando TODAS as 6 falham o
 * pacote aborta (documento fantasma sem conteudo nao e aceitavel).
 *
 * <p><strong>Preview</strong>: HTML leve listando secoes, sem gerar o pacote
 * pesado (para economizar tempo de resposta).
 */
@Component
public class RegulatorioPacoteGenerator implements ReportGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(RegulatorioPacoteGenerator.class);
    private static final Locale PT_BR = ReportV2PdfTheme.PT_BR;

    private final CqOperationalV2Generator cqGen;
    private final WestgardDeepdiveGenerator westgardGen;
    private final ReagentesRastreabilidadeGenerator reagentesGen;
    private final ManutencaoKpiGenerator manutencaoGen;
    private final CalibracaoPrePostGenerator calibracaoGen;
    private final MultiAreaConsolidadoGenerator multiAreaGen;

    private final ReportNumberingService reportNumberingService;
    private final LabHeaderRenderer headerRenderer;
    private final LabSettingsService labSettingsService;
    private final SubordinateInvocation subordinate;

    public RegulatorioPacoteGenerator(
        CqOperationalV2Generator cqGen,
        WestgardDeepdiveGenerator westgardGen,
        ReagentesRastreabilidadeGenerator reagentesGen,
        ManutencaoKpiGenerator manutencaoGen,
        CalibracaoPrePostGenerator calibracaoGen,
        MultiAreaConsolidadoGenerator multiAreaGen,
        ReportNumberingService reportNumberingService,
        LabHeaderRenderer headerRenderer,
        LabSettingsService labSettingsService,
        SubordinateInvocation subordinate
    ) {
        this.cqGen = cqGen;
        this.westgardGen = westgardGen;
        this.reagentesGen = reagentesGen;
        this.manutencaoGen = manutencaoGen;
        this.calibracaoGen = calibracaoGen;
        this.multiAreaGen = multiAreaGen;
        this.reportNumberingService = reportNumberingService;
        this.headerRenderer = headerRenderer;
        this.labSettingsService = labSettingsService;
        this.subordinate = subordinate;
    }

    @Override
    public ReportDefinition definition() {
        return ReportDefinitionRegistry.REGULATORIO_PACOTE_DEFINITION;
    }

    @Override
    @Transactional
    public ReportArtifact generate(ReportFilters filters, GenerationContext ctx) {
        String reportNumber = reportNumberingService.reserveNextNumber();
        String periodLabel = periodLabel(filters);

        List<SectionPdf> sections = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        runSection(sections, warnings, "Relatorio Operacional de CQ",
            () -> cqGen.generate(forwardCqFilters(filters), ctx).bytes());
        runSection(sections, warnings, "Westgard Deepdive",
            () -> westgardGen.generate(forwardWestgardFilters(filters), ctx).bytes());
        runSection(sections, warnings, "Rastreabilidade de Reagentes",
            () -> reagentesGen.generate(forwardEmpty(filters), ctx).bytes());
        runSection(sections, warnings, "KPIs de Manutencao",
            () -> manutencaoGen.generate(forwardPeriodOnly(filters), ctx).bytes());
        runSection(sections, warnings, "Calibracao Pre/Pos",
            () -> calibracaoGen.generate(forwardPeriodOnly(filters), ctx).bytes());
        runSection(sections, warnings, "Consolidado Multi-area",
            () -> multiAreaGen.generate(forwardMultiAreaFilters(filters), ctx).bytes());

        // Garantia regulatoria: pacote vazio e documento fantasma. Se todas as
        // 6 secoes falharam, aborta para nao emitir laudo com numero + auditoria
        // sem conteudo real.
        if (sections.isEmpty()) {
            throw new IllegalStateException(
                "Pacote regulatorio nao pode ser emitido: todas as 6 secoes falharam na geracao. "
                + "Verifique logs dos subordinados."
            );
        }

        byte[] merged = mergePackage(sections, ctx, reportNumber, periodLabel, warnings);
        String sha256 = reportNumberingService.sha256Hex(merged);
        reportNumberingService.registerGeneration(reportNumber, "regulatorio", "PDF", periodLabel,
            merged, ctx == null ? null : ctx.userId());
        return new ReportArtifact(merged, "application/pdf", reportNumber + ".pdf", 0,
            merged.length, reportNumber, sha256, periodLabel, List.copyOf(warnings));
    }

    /**
     * Invoca {@code task} em tx isolada (REQUIRES_NEW). Em caso de falha,
     * adiciona warning e segue para a proxima secao.
     */
    private void runSection(
        List<SectionPdf> out,
        List<String> warnings,
        String title,
        Supplier<byte[]> task
    ) {
        Optional<byte[]> result = subordinate.runIsolated(title, task);
        if (result.isPresent()) {
            out.add(new SectionPdf(title, result.get()));
        } else {
            warnings.add("Secao '" + title + "' falhou — conteudo omitido");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ReportPreview preview(ReportFilters filters, GenerationContext ctx) {
        String periodLabel = periodLabel(filters);
        StringBuilder html = new StringBuilder();
        html.append("<section><h1 style=\"color:#14532d\">Pacote Regulatorio</h1>");
        html.append("<p>Periodo: ").append(periodLabel).append("</p>");
        html.append("<p>O pacote incluira as seguintes secoes:</p>");
        html.append("<ol>")
            .append("<li>Declaracao de Autenticidade</li>")
            .append("<li>Relatorio Operacional de CQ</li>")
            .append("<li>Westgard Deepdive</li>")
            .append("<li>Rastreabilidade de Reagentes</li>")
            .append("<li>KPIs de Manutencao</li>")
            .append("<li>Calibracao Pre/Pos</li>")
            .append("<li>Consolidado Multi-area</li>")
            .append("</ol></section>");
        return new ReportPreview(html.toString(), List.of(), periodLabel);
    }

    // ---------- merge ----------

    private byte[] mergePackage(List<SectionPdf> sections, GenerationContext ctx,
                                String reportNumber, String periodLabel,
                                List<String> warnings) {
        LabSettings settings = ctx != null && ctx.labSettings() != null ? ctx.labSettings()
            : labSettingsService.getOrCreateSingleton();
        String respName = settings == null ? "" : settings.getResponsibleName();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Passo 1: gerar declaracao de autenticidade como PDF independente
            byte[] declaration = buildDeclaration(reportNumber, periodLabel, ctx, warnings);

            // Passo 2: concatenar com PdfCopy
            Document mergedDoc = new Document(PageSize.A4);
            PdfCopy copy = new PdfCopy(mergedDoc, out);
            mergedDoc.open();
            appendPdf(copy, declaration);
            for (SectionPdf s : sections) {
                appendPdf(copy, s.bytes());
            }
            mergedDoc.close();

            // Passo 3: adicionar rodape global (stamper)
            return stampFooter(out.toByteArray(), reportNumber, respName);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao unir pacote regulatorio", ex);
        }
    }

    private void appendPdf(PdfCopy copy, byte[] pdf) throws Exception {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(pdf)) {
            PdfReader reader = new PdfReader(bis);
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                copy.addPage(copy.getImportedPage(reader, i));
            }
            reader.close();
        }
    }

    private byte[] buildDeclaration(String reportNumber, String periodLabel, GenerationContext ctx,
                                    List<String> warnings) {
        LabSettings settings = ctx != null && ctx.labSettings() != null ? ctx.labSettings()
            : labSettingsService.getOrCreateSingleton();
        String respName = settings == null ? "-" : settings.getResponsibleName();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 54F, 54F, 54F, 54F);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PdfFooterRenderer(reportNumber, respName));
            doc.open();
            ReportArtifact headerArtifact = new ReportArtifact(
                new byte[] { 0x25, 0x50 }, "application/pdf", reportNumber + ".pdf", 1, 2L,
                reportNumber, "0000000000000000000000000000000000000000000000000000000000000000",
                periodLabel);
            headerRenderer.render(doc, writer, settings, definition(), headerArtifact);

            Paragraph title = new Paragraph("Declaracao de Autenticidade", ReportV2PdfTheme.SECTION_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(16F);
            doc.add(title);

            String body = "O presente pacote regulatorio consolida os relatorios do periodo "
                + periodLabel + " gerados pelo sistema BioQC. Todas as secoes internas "
                + "possuem sua propria numeracao e hash. Este documento e entregavel a vigilancia "
                + "sanitaria com fins de auditoria e transparencia do controle laboratorial.";
            Paragraph p = new Paragraph(body, ReportV2PdfTheme.BODY_FONT);
            p.setAlignment(Element.ALIGN_JUSTIFIED);
            p.setSpacingAfter(20F);
            doc.add(p);

            // T2: quando ha secoes falhadas, destacar em caixa de atencao
            // para que o operador nao entregue um pacote parcial acreditando
            // que esta integral.
            if (warnings != null && !warnings.isEmpty()) {
                PdfPTable wrap = new PdfPTable(1);
                wrap.setWidthPercentage(100F);
                StringBuilder sb = new StringBuilder();
                sb.append("ATENCAO: este pacote foi gerado parcialmente. ")
                  .append(warnings.size()).append(" secao(oes) falharam e foram omitidas:\n");
                for (String w : warnings) {
                    sb.append("  - ").append(w).append('\n');
                }
                PdfPCell cell = new PdfPCell(new Phrase(sb.toString(), ReportV2PdfTheme.AI_FONT));
                cell.setBackgroundColor(ReportV2PdfTheme.ALERT_BG);
                cell.setBorderColor(ReportV2PdfTheme.STATUS_REPROVADO);
                cell.setPadding(10F);
                wrap.addCell(cell);
                wrap.setSpacingAfter(14F);
                doc.add(wrap);
            }

            Paragraph sumTitle = new Paragraph("Indice", ReportV2PdfTheme.SUBSECTION_FONT);
            sumTitle.setSpacingAfter(6F);
            doc.add(sumTitle);
            String[] items = {
                "1. Relatorio Operacional de CQ",
                "2. Westgard Deepdive",
                "3. Rastreabilidade de Reagentes",
                "4. KPIs de Manutencao",
                "5. Calibracao Pre/Pos",
                "6. Consolidado Multi-area"
            };
            for (String item : items) {
                doc.add(new Paragraph(item, ReportV2PdfTheme.BODY_FONT));
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao gerar declaracao de autenticidade", ex);
        }
    }

    private byte[] stampFooter(byte[] merged, String reportNumber, String respName) {
        // Stamper para adicionar rodape seria ideal aqui. Como PdfCopy ja
        // incluiu os rodapes das paginas individuais, mantemos sem re-stamp
        // (evita re-parse pesado). Iteracao futura pode substituir por
        // PdfStamper + PdfFooterRenderer para renumerar paginas globais.
        return merged;
    }

    // ---------- filtros forward ----------

    private ReportFilters forwardCqFilters(ReportFilters base) {
        // Forward periodo + IA off por default (governanca: pacote regulatorio nao usa IA);
        // historico diario LIGADO por default — vigilancia precisa ver "o que aconteceu em
        // cada dia" no pacote ANVISA.
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("area", base.getString("areas").map(a -> "bioquimica").orElse("bioquimica"));
        m.put("periodType", base.getString("periodType").orElse("current-month"));
        m.put("month", base.getInteger("month").orElse(LocalDate.now().getMonthValue()));
        m.put("year", base.getInteger("year").orElse(LocalDate.now().getYear()));
        m.put("includeDailyHistory", true);
        return new ReportFilters(m);
    }

    private ReportFilters forwardWestgardFilters(ReportFilters base) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("area", "bioquimica");
        m.put("periodType", base.getString("periodType").orElse("current-month"));
        base.getInteger("month").ifPresent(v -> m.put("month", v));
        base.getInteger("year").ifPresent(v -> m.put("year", v));
        return new ReportFilters(m);
    }

    private ReportFilters forwardEmpty(ReportFilters base) {
        return new ReportFilters(java.util.Map.of());
    }

    private ReportFilters forwardPeriodOnly(ReportFilters base) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("periodType", base.getString("periodType").orElse("current-month"));
        base.getInteger("month").ifPresent(v -> m.put("month", v));
        base.getInteger("year").ifPresent(v -> m.put("year", v));
        return new ReportFilters(m);
    }

    private ReportFilters forwardMultiAreaFilters(ReportFilters base) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        List<String> areas = base.getStringList("areas").orElse(ReportDefinitionRegistry.AREAS);
        m.put("areas", areas);
        m.put("periodType", base.getString("periodType").orElse("current-month"));
        base.getInteger("month").ifPresent(v -> m.put("month", v));
        base.getInteger("year").ifPresent(v -> m.put("year", v));
        return new ReportFilters(m);
    }

    private String periodLabel(ReportFilters filters) {
        String type = filters.getString("periodType").orElse("current-month");
        LocalDate today = LocalDate.now();
        if ("year".equals(type)) {
            int y = filters.getInteger("year").orElse(today.getYear());
            return "Ano " + y;
        }
        if ("specific-month".equals(type)) {
            int m = filters.getInteger("month").orElse(today.getMonthValue());
            int y = filters.getInteger("year").orElse(today.getYear());
            YearMonth ym = YearMonth.of(y, m);
            return capitalize(ym.getMonth().getDisplayName(TextStyle.FULL, PT_BR)) + "/" + y;
        }
        YearMonth ym = YearMonth.now();
        return capitalize(ym.getMonth().getDisplayName(TextStyle.FULL, PT_BR)) + "/" + ym.getYear();
    }

    private static String capitalize(String v) {
        if (v == null || v.isEmpty()) return "";
        return v.substring(0, 1).toUpperCase(PT_BR) + v.substring(1);
    }

    record SectionPdf(String title, byte[] bytes) {}
}
