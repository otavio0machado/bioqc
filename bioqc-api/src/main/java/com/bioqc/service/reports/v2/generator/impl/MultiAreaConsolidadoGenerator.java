package com.bioqc.service.reports.v2.generator.impl;

import com.bioqc.entity.LabSettings;
import com.bioqc.entity.MaintenanceRecord;
import com.bioqc.entity.QcRecord;
import com.bioqc.entity.ReagentLot;
import com.bioqc.repository.MaintenanceRecordRepository;
import com.bioqc.repository.QcRecordRepository;
import com.bioqc.repository.ReagentLotRepository;
import com.bioqc.repository.WestgardViolationRepository;
import com.bioqc.service.LabSettingsService;
import com.bioqc.service.ReportNumberingService;
import com.bioqc.service.reports.v2.catalog.ReportCode;
import com.bioqc.service.reports.v2.catalog.ReportDefinition;
import com.bioqc.service.reports.v2.catalog.ReportDefinitionRegistry;
import com.bioqc.service.reports.v2.generator.GenerationContext;
import com.bioqc.service.reports.v2.generator.ReportArtifact;
import com.bioqc.service.reports.v2.generator.ReportFilters;
import com.bioqc.service.reports.v2.generator.ReportGenerator;
import com.bioqc.service.reports.v2.generator.ReportPreview;
import com.bioqc.service.reports.v2.generator.ai.ReportAiCommentator;
import com.bioqc.service.reports.v2.generator.chart.ChartRenderer;
import com.bioqc.service.reports.v2.generator.pdf.LabHeaderRenderer;
import com.bioqc.service.reports.v2.generator.pdf.PdfFooterRenderer;
import com.bioqc.service.reports.v2.generator.pdf.ReportV2PdfTheme;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MultiAreaConsolidadoGenerator implements ReportGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(MultiAreaConsolidadoGenerator.class);
    private static final Locale PT_BR = ReportV2PdfTheme.PT_BR;

    private final QcRecordRepository qcRecordRepository;
    private final WestgardViolationRepository violationRepository;
    private final ReagentLotRepository reagentLotRepository;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final ReportNumberingService reportNumberingService;
    private final ChartRenderer chartRenderer;
    private final LabHeaderRenderer headerRenderer;
    private final LabSettingsService labSettingsService;
    private final ReportAiCommentator aiCommentator;

    public MultiAreaConsolidadoGenerator(
        QcRecordRepository qcRecordRepository,
        WestgardViolationRepository violationRepository,
        ReagentLotRepository reagentLotRepository,
        MaintenanceRecordRepository maintenanceRepository,
        ReportNumberingService reportNumberingService,
        ChartRenderer chartRenderer,
        LabHeaderRenderer headerRenderer,
        LabSettingsService labSettingsService,
        ReportAiCommentator aiCommentator
    ) {
        this.qcRecordRepository = qcRecordRepository;
        this.violationRepository = violationRepository;
        this.reagentLotRepository = reagentLotRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.reportNumberingService = reportNumberingService;
        this.chartRenderer = chartRenderer;
        this.headerRenderer = headerRenderer;
        this.labSettingsService = labSettingsService;
        this.aiCommentator = aiCommentator;
    }

    @Override
    public ReportDefinition definition() {
        return ReportDefinitionRegistry.MULTI_AREA_CONSOLIDADO_DEFINITION;
    }

    @Override
    @Transactional
    public ReportArtifact generate(ReportFilters filters, GenerationContext ctx) {
        Resolved rf = resolve(filters);
        String reportNumber = reportNumberingService.reserveNextNumber();
        byte[] pdfBytes = renderPdf(rf, ctx, reportNumber);
        String sha256 = reportNumberingService.sha256Hex(pdfBytes);
        reportNumberingService.registerGeneration(reportNumber, "multi-area", "PDF", rf.periodLabel,
            pdfBytes, ctx == null ? null : ctx.userId());
        return new ReportArtifact(pdfBytes, "application/pdf", reportNumber + ".pdf", 0,
            pdfBytes.length, reportNumber, sha256, rf.periodLabel);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportPreview preview(ReportFilters filters, GenerationContext ctx) {
        Resolved rf = resolve(filters);
        StringBuilder html = new StringBuilder();
        html.append("<section><h1 style=\"color:#14532d\">Relatorio Consolidado do Laboratorio</h1>");
        html.append("<p>Areas: ").append(rf.areas == null ? "-" : String.join(", ", rf.areas))
            .append(" - Periodo: ").append(rf.periodLabel).append("</p></section>");
        return new ReportPreview(html.toString(), List.of(), rf.periodLabel);
    }

    private byte[] renderPdf(Resolved rf, GenerationContext ctx, String reportNumber) {
        LabSettings settings = ctx != null && ctx.labSettings() != null ? ctx.labSettings() : labSettingsService.getOrCreateSingleton();
        String respName = settings == null ? "" : settings.getResponsibleName();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36F, 36F, 40F, 54F);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PdfFooterRenderer(reportNumber, respName));
            doc.open();
            ReportArtifact headerArtifact = new ReportArtifact(
                new byte[] { 0x25, 0x50 }, "application/pdf", reportNumber + ".pdf", 1, 2L,
                reportNumber, "0000000000000000000000000000000000000000000000000000000000000000",
                rf.periodLabel);
            headerRenderer.render(doc, writer, settings, definition(), headerArtifact);

            doc.add(ReportV2PdfTheme.section("Consolidado por area"));
            PdfPTable t = ReportV2PdfTheme.table(new float[] {2F, 1.3F, 1.3F, 1.3F, 1.3F, 1.5F});
            ReportV2PdfTheme.headerRow(t, "Area", "Registros", "Taxa aprov.", "Alertas", "Reagentes criticos", "Manut. pendentes");
            boolean alt = false;
            Map<String, Number> rateChart = new LinkedHashMap<>();
            List<String> areas = rf.areas == null || rf.areas.isEmpty()
                ? ReportDefinitionRegistry.AREAS
                : rf.areas;
            // T5: calcula reagentes criticos/manutencoes pendentes uma unica
            // vez fora do loop (eram invariantes por area, recalculados seis
            // vezes). Alem de corrigir o uso abusivo de findAll, garante
            // coerencia entre a tabela e o card de alertas abaixo.
            LocalDate today = LocalDate.now();
            long reagCrit = reagentLotRepository.countExpiredWithStock(today);
            long manutPend = maintenanceRepository.findOverdue(today).size();

            for (String area : areas) {
                List<QcRecord> recs = qcRecordRepository.findByAreaAndDateRange(area, rf.start, rf.end);
                int total = recs.size();
                long aprovados = recs.stream().filter(r -> "APROVADO".equalsIgnoreCase(r.getStatus())).count();
                long alertas = recs.stream().filter(r -> "ALERTA".equalsIgnoreCase(r.getStatus())).count();
                double taxa = total == 0 ? 0 : (aprovados * 100.0 / total);
                rateChart.put(area, taxa);

                ReportV2PdfTheme.bodyRow(t, alt,
                    capitalize(area),
                    String.valueOf(total),
                    String.format(PT_BR, "%.1f%%", taxa),
                    String.valueOf(alertas),
                    String.valueOf(reagCrit),
                    String.valueOf(manutPend)
                );
                alt = !alt;
            }
            doc.add(t);

            // Grafico de taxa de aprovacao por area
            if (!rateChart.isEmpty()) {
                try {
                    byte[] png = chartRenderer.renderBarChart(rateChart,
                        "Taxa de aprovacao por area", "Area", "Taxa %");
                    Image img = Image.getInstance(png);
                    img.scaleToFit(500F, 220F);
                    img.setAlignment(Element.ALIGN_CENTER);
                    doc.add(img);
                } catch (Exception ex) {
                    LOG.warn("Falha chart consolidado", ex);
                }
            }

            // Alertas transversais — T5: todas queries janeladas/contadores
            doc.add(ReportV2PdfTheme.section("Alertas transversais"));
            // Rejeicoes Westgard do periodo, todas areas (area=null). Filtramos
            // severidade no stream porque o banco tem tanto "REJEICAO" (legado)
            // quanto "REJECTION" (padrao novo) e nao queremos acoplar isso a query.
            long rejeicoesGraves = violationRepository.findByAreaAndPeriod(null, rf.start, rf.end).stream()
                .filter(v -> "REJEICAO".equalsIgnoreCase(v.getSeverity()) || "REJECTION".equalsIgnoreCase(v.getSeverity()))
                .count();
            long reagVencidos = reagCrit;           // reaproveita contagem feita acima
            long manutAtrasadas = manutPend;        // idem

            PdfPTable alertTable = ReportV2PdfTheme.table(new float[] {3F, 1F});
            ReportV2PdfTheme.headerRow(alertTable, "Alerta", "Ocorrencias");
            ReportV2PdfTheme.bodyRow(alertTable, false, "Violacoes Westgard REJEICAO", String.valueOf(rejeicoesGraves));
            ReportV2PdfTheme.bodyRow(alertTable, true, "Reagentes vencidos com estoque", String.valueOf(reagVencidos));
            ReportV2PdfTheme.bodyRow(alertTable, false, "Manutencoes atrasadas", String.valueOf(manutAtrasadas));
            doc.add(alertTable);

            // Detalhamento por area — uma secao completa por area
            if (rf.detailEachArea && !areas.isEmpty()) {
                doc.newPage();
                doc.add(ReportV2PdfTheme.section("Detalhamento por area"));
                Paragraph intro = new Paragraph(
                    "Cada area abaixo aparece em sua propria secao com taxa de aprovacao, "
                    + "top exames problematicos, distribuicao por status e violacoes Westgard "
                    + "no periodo.",
                    ReportV2PdfTheme.META_FONT);
                intro.setSpacingAfter(8F);
                doc.add(intro);

                boolean first = true;
                for (String area : areas) {
                    if (!first) doc.newPage();
                    first = false;
                    renderAreaDetail(doc, area, rf);
                }
            }

            if (rf.includeAiCommentary) {
                doc.add(ReportV2PdfTheme.section("Visao executiva"));
                StringBuilder sb = new StringBuilder();
                sb.append("Areas: ").append(areas).append('\n');
                sb.append("Periodo: ").append(rf.periodLabel).append('\n');
                sb.append("Rejeicoes graves: ").append(rejeicoesGraves).append('\n');
                sb.append("Reagentes vencidos com estoque: ").append(reagVencidos).append('\n');
                sb.append("Manutencoes atrasadas: ").append(manutAtrasadas).append('\n');
                String commentary = aiCommentator.commentary(ReportCode.MULTI_AREA_CONSOLIDADO, sb.toString(), ctx);
                PdfPTable wrap = new PdfPTable(1);
                wrap.setWidthPercentage(100F);
                PdfPCell cell = new PdfPCell(new Phrase(commentary, ReportV2PdfTheme.AI_FONT));
                cell.setBackgroundColor(ReportV2PdfTheme.BRAND_LIGHT);
                cell.setBorderColor(ReportV2PdfTheme.BRAND_DARK);
                cell.setPadding(10F);
                wrap.addCell(cell);
                doc.add(wrap);
            }

            doc.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException ex) {
            throw new IllegalStateException("Falha ao gerar PDF consolidado", ex);
        }
    }

    private Resolved resolve(ReportFilters filters) {
        Resolved r = new Resolved();
        r.areas = filters.getStringList("areas").orElse(null);
        r.includeAiCommentary = filters.getBoolean("includeAiCommentary").orElse(false);
        r.detailEachArea = filters.getBoolean("detailEachArea").orElse(true);
        String periodType = filters.getString("periodType")
            .map(s -> s.trim().toLowerCase(Locale.ROOT)).orElse("current-month");
        LocalDate today = LocalDate.now();
        switch (periodType) {
            case "year" -> {
                int y = filters.getInteger("year").orElse(today.getYear());
                r.start = LocalDate.of(y, 1, 1); r.end = LocalDate.of(y, 12, 31);
                r.periodLabel = "Ano " + y;
            }
            case "specific-month" -> {
                int m = filters.getInteger("month").orElse(today.getMonthValue());
                int y = filters.getInteger("year").orElse(today.getYear());
                YearMonth ym = YearMonth.of(y, m);
                r.start = ym.atDay(1); r.end = ym.atEndOfMonth();
                r.periodLabel = capitalize(ym.getMonth().getDisplayName(TextStyle.FULL, PT_BR)) + "/" + y;
            }
            case "date-range" -> {
                r.start = filters.getDate("dateFrom").orElseThrow();
                r.end = filters.getDate("dateTo").orElseThrow();
                r.periodLabel = r.start.format(DateTimeFormatter.ISO_DATE) + " a " + r.end.format(DateTimeFormatter.ISO_DATE);
            }
            default -> {
                YearMonth ym = YearMonth.now();
                r.start = ym.atDay(1); r.end = ym.atEndOfMonth();
                r.periodLabel = capitalize(ym.getMonth().getDisplayName(TextStyle.FULL, PT_BR)) + "/" + ym.getYear();
            }
        }
        return r;
    }

    private static String capitalize(String v) {
        if (v == null || v.isEmpty()) return "";
        return v.substring(0, 1).toUpperCase(PT_BR) + v.substring(1);
    }

    static final class Resolved {
        List<String> areas;
        LocalDate start;
        LocalDate end;
        String periodLabel;
        boolean includeAiCommentary;
        boolean detailEachArea;
    }

    private PdfPCell summaryCell(String label, String value, java.awt.Color color) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(8F);
        cell.setBorderColor(ReportV2PdfTheme.BORDER);
        Paragraph l = new Paragraph(label, ReportV2PdfTheme.META_FONT);
        l.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(l);
        Paragraph v = new Paragraph(value,
            com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 14, color));
        v.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(v);
        return cell;
    }

    /**
     * Renderiza UMA secao por area com:
     * - Cabecalho com nome da area
     * - Cards: total registros, aprovados, alertas, reprovados, taxa
     * - Top 5 exames com pior taxa de aprovacao
     * - Distribuicao de violacoes Westgard por regra
     * - Tabela de exames mais ativos (com taxa de aprovacao)
     */
    private void renderAreaDetail(Document doc, String area, Resolved rf) throws DocumentException {
        Paragraph h = new Paragraph(capitalize(area),
            com.lowagie.text.FontFactory.getFont(
                com.lowagie.text.FontFactory.HELVETICA_BOLD, 14, ReportV2PdfTheme.BRAND_DARK));
        h.setSpacingBefore(4F); h.setSpacingAfter(6F);
        doc.add(h);

        java.util.List<com.bioqc.entity.QcRecord> recs =
            qcRecordRepository.findByAreaAndDateRange(area, rf.start, rf.end);

        long total = recs.size();
        long aprov = recs.stream().filter(r -> "APROVADO".equalsIgnoreCase(r.getStatus())).count();
        long alerta = recs.stream().filter(r -> "ALERTA".equalsIgnoreCase(r.getStatus())).count();
        long reprov = recs.stream().filter(r -> "REPROVADO".equalsIgnoreCase(r.getStatus())).count();
        double taxa = total == 0 ? 0 : (aprov * 100.0 / total);

        PdfPTable cards = new PdfPTable(new float[] {1, 1, 1, 1, 1});
        cards.setWidthPercentage(100F); cards.setSpacingAfter(8F);
        cards.addCell(summaryCell("Total", String.valueOf(total), ReportV2PdfTheme.BRAND_PRIMARY));
        cards.addCell(summaryCell("Aprovados", String.valueOf(aprov), ReportV2PdfTheme.STATUS_APROVADO));
        cards.addCell(summaryCell("Alertas", String.valueOf(alerta), ReportV2PdfTheme.STATUS_ALERTA));
        cards.addCell(summaryCell("Reprovados", String.valueOf(reprov), ReportV2PdfTheme.STATUS_REPROVADO));
        cards.addCell(summaryCell("Taxa aprov.", String.format(PT_BR, "%.1f%%", taxa),
            taxa >= 90 ? ReportV2PdfTheme.STATUS_APROVADO
                : (taxa >= 70 ? ReportV2PdfTheme.STATUS_ALERTA : ReportV2PdfTheme.STATUS_REPROVADO)));
        doc.add(cards);

        if (recs.isEmpty()) {
            Paragraph empty = new Paragraph(
                "Sem registros de CQ desta area no periodo selecionado.",
                ReportV2PdfTheme.META_FONT);
            empty.setSpacingAfter(8F);
            doc.add(empty);
            return;
        }

        // Top exames por taxa de reprovacao
        Map<String, long[]> byExam = new LinkedHashMap<>();  // [total, reprovados]
        for (com.bioqc.entity.QcRecord r : recs) {
            String key = r.getExamName() == null ? "—" : r.getExamName();
            long[] counts = byExam.computeIfAbsent(key, k -> new long[2]);
            counts[0]++;
            if (!"APROVADO".equalsIgnoreCase(r.getStatus())) counts[1]++;
        }

        doc.add(ReportV2PdfTheme.subsection("Exames mais ativos"));
        java.util.List<Map.Entry<String, long[]>> examList = byExam.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
            .limit(15)
            .collect(Collectors.toList());
        PdfPTable et = ReportV2PdfTheme.table(new float[] {2.5F, 1F, 1F, 1F, 1.2F});
        ReportV2PdfTheme.headerRow(et, "Exame", "Total", "Nao-aprovados", "Taxa rejeicao", "Status");
        boolean alt = false;
        for (Map.Entry<String, long[]> e : examList) {
            long t = e.getValue()[0];
            long bad = e.getValue()[1];
            double rej = t == 0 ? 0 : (bad * 100.0 / t);
            String status = rej > 20 ? "ATENCAO" : (rej > 10 ? "Monitorar" : "OK");
            ReportV2PdfTheme.bodyRow(et, alt,
                ReportV2PdfTheme.safe(e.getKey()),
                String.valueOf(t),
                String.valueOf(bad),
                String.format(PT_BR, "%.1f%%", rej),
                status);
            alt = !alt;
        }
        doc.add(et);

        // Violacoes Westgard desta area
        java.util.List<com.bioqc.entity.WestgardViolation> viols =
            violationRepository.findByAreaAndPeriod(area, rf.start, rf.end);
        if (!viols.isEmpty()) {
            doc.add(ReportV2PdfTheme.subsection("Violacoes Westgard ("
                + viols.size() + " no periodo)"));
            Map<String, Long> byRule = viols.stream()
                .filter(v -> v.getRule() != null)
                .collect(Collectors.groupingBy(
                    com.bioqc.entity.WestgardViolation::getRule, Collectors.counting()));
            String rulesStr = byRule.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.joining(", "));
            Paragraph rp = new Paragraph(
                rulesStr.isEmpty() ? "Sem regras categorizadas." : rulesStr,
                ReportV2PdfTheme.BODY_FONT);
            rp.setSpacingAfter(8F);
            doc.add(rp);
        }
    }
}
