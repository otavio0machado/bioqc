package com.bioqc.service.reports.v2.generator.impl;

import com.bioqc.entity.LabSettings;
import com.bioqc.entity.WestgardViolation;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generator de deep-dive em violacoes Westgard. Seccoes:
 * 1. Capa institucional
 * 2. Resumo (total, por severidade)
 * 3. Top 10 regras (barChart + tabela)
 * 4. Exames mais problematicos (barChart)
 * 5. Heatmap temporal dia-semana x semana-mes
 * 6. Lista detalhada top 30
 * 7. Comentario IA
 */
@Component
public class WestgardDeepdiveGenerator implements ReportGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(WestgardDeepdiveGenerator.class);
    private static final Locale PT_BR = ReportV2PdfTheme.PT_BR;

    private final WestgardViolationRepository violationRepository;
    private final ReportNumberingService reportNumberingService;
    private final ChartRenderer chartRenderer;
    private final LabHeaderRenderer headerRenderer;
    private final LabSettingsService labSettingsService;
    private final ReportAiCommentator aiCommentator;

    public WestgardDeepdiveGenerator(
        WestgardViolationRepository violationRepository,
        ReportNumberingService reportNumberingService,
        ChartRenderer chartRenderer,
        LabHeaderRenderer headerRenderer,
        LabSettingsService labSettingsService,
        ReportAiCommentator aiCommentator
    ) {
        this.violationRepository = violationRepository;
        this.reportNumberingService = reportNumberingService;
        this.chartRenderer = chartRenderer;
        this.headerRenderer = headerRenderer;
        this.labSettingsService = labSettingsService;
        this.aiCommentator = aiCommentator;
    }

    @Override
    public ReportDefinition definition() {
        return ReportDefinitionRegistry.WESTGARD_DEEPDIVE_DEFINITION;
    }

    @Override
    @Transactional
    public ReportArtifact generate(ReportFilters filters, GenerationContext ctx) {
        Resolved rf = resolve(filters);
        rf.includeAiCommentary = filters.getBoolean("includeAiCommentary").orElse(false);
        String reportNumber = reportNumberingService.reserveNextNumber();
        byte[] pdfBytes = renderPdf(rf, ctx, reportNumber);
        String sha256 = reportNumberingService.sha256Hex(pdfBytes);
        reportNumberingService.registerGeneration(reportNumber, rf.area, "PDF", rf.periodLabel,
            pdfBytes, ctx == null ? null : ctx.userId());
        return new ReportArtifact(pdfBytes, "application/pdf", reportNumber + ".pdf", 0,
            pdfBytes.length, reportNumber, sha256, rf.periodLabel);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportPreview preview(ReportFilters filters, GenerationContext ctx) {
        Resolved rf = resolve(filters);
        List<WestgardViolation> violations = loadViolations(rf);
        StringBuilder html = new StringBuilder();
        html.append("<section><h1 style=\"color:#14532d\">Analise Profunda de Westgard</h1>");
        html.append("<p>Area: ").append(rf.area).append(" - Periodo: ").append(rf.periodLabel).append("</p>");
        html.append("<p>Total de violacoes: <strong>").append(violations.size()).append("</strong></p>");
        List<String> warnings = violations.isEmpty()
            ? List.of("Nenhuma violacao encontrada no periodo selecionado.")
            : List.of();
        html.append("</section>");
        return new ReportPreview(html.toString(), warnings, rf.periodLabel);
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
                new byte[] { 0x25, 0x50 }, "application/pdf", reportNumber + ".pdf",
                1, 2L, reportNumber,
                "0000000000000000000000000000000000000000000000000000000000000000",
                rf.periodLabel);
            headerRenderer.render(doc, writer, settings, definition(), headerArtifact);

            List<WestgardViolation> violations = loadViolations(rf);

            // Resumo
            doc.add(ReportV2PdfTheme.section("Resumo"));
            long advertencias = violations.stream()
                .filter(v -> "ADVERTENCIA".equalsIgnoreCase(v.getSeverity())).count();
            long rejeicoes = violations.stream()
                .filter(v -> "REJEICAO".equalsIgnoreCase(v.getSeverity()) || "REJECTION".equalsIgnoreCase(v.getSeverity())).count();
            long exames = violations.stream()
                .filter(v -> v.getQcRecord() != null)
                .map(v -> v.getQcRecord().getExamName()).filter(java.util.Objects::nonNull).distinct().count();
            PdfPTable summary = new PdfPTable(new float[] {1, 1, 1, 1});
            summary.setWidthPercentage(100F);
            summary.setSpacingAfter(6F);
            summary.addCell(summaryCell("Total", String.valueOf(violations.size()), ReportV2PdfTheme.BRAND_PRIMARY));
            summary.addCell(summaryCell("Advertencias", String.valueOf(advertencias), ReportV2PdfTheme.STATUS_ALERTA));
            summary.addCell(summaryCell("Rejeicoes", String.valueOf(rejeicoes), ReportV2PdfTheme.STATUS_REPROVADO));
            summary.addCell(summaryCell("Exames afetados", String.valueOf(exames), ReportV2PdfTheme.BRAND_DARK));
            doc.add(summary);

            if (violations.isEmpty()) {
                doc.add(new Paragraph("Nenhuma violacao registrada no periodo.", ReportV2PdfTheme.BODY_FONT));
                doc.close();
                return out.toByteArray();
            }

            // Top regras
            Map<String, Long> byRule = violations.stream()
                .collect(Collectors.groupingBy(
                    v -> v.getRule() == null ? "-" : v.getRule(),
                    LinkedHashMap::new, Collectors.counting()));
            List<Map.Entry<String, Long>> topRules = byRule.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10).collect(Collectors.toList());

            doc.add(ReportV2PdfTheme.section("Top 10 regras violadas"));
            Map<String, Number> ruleDs = new LinkedHashMap<>();
            topRules.forEach(e -> ruleDs.put(e.getKey(), e.getValue()));
            try {
                byte[] png = chartRenderer.renderBarChart(ruleDs, "Violacoes por regra", "Regra", "Ocorrencias");
                Image img = Image.getInstance(png);
                img.scaleToFit(500F, 220F);
                img.setAlignment(Element.ALIGN_CENTER);
                doc.add(img);
            } catch (Exception ex) {
                LOG.warn("Falha ao renderizar barChart de regras", ex);
            }
            PdfPTable rulesT = ReportV2PdfTheme.table(new float[] {2F, 1.5F, 1.5F});
            ReportV2PdfTheme.headerRow(rulesT, "Regra", "Ocorrencias", "% do total");
            boolean alt = false;
            for (Map.Entry<String, Long> e : topRules) {
                double pct = violations.isEmpty() ? 0 : (e.getValue() * 100.0 / violations.size());
                ReportV2PdfTheme.bodyRow(rulesT, alt, e.getKey(), String.valueOf(e.getValue()),
                    String.format(PT_BR, "%.1f%%", pct));
                alt = !alt;
            }
            doc.add(rulesT);

            // Exames mais problematicos
            Map<String, Long> byExam = violations.stream()
                .filter(v -> v.getQcRecord() != null)
                .collect(Collectors.groupingBy(
                    v -> v.getQcRecord().getExamName() == null ? "-" : v.getQcRecord().getExamName(),
                    LinkedHashMap::new, Collectors.counting()));
            List<Map.Entry<String, Long>> topExames = byExam.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10).collect(Collectors.toList());
            if (!topExames.isEmpty()) {
                doc.add(ReportV2PdfTheme.section("Exames mais problematicos"));
                Map<String, Number> examDs = new LinkedHashMap<>();
                topExames.forEach(e -> examDs.put(e.getKey(), e.getValue()));
                try {
                    byte[] png = chartRenderer.renderBarChart(examDs, "Violacoes por exame", "Exame", "Ocorrencias");
                    Image img = Image.getInstance(png);
                    img.scaleToFit(500F, 220F);
                    img.setAlignment(Element.ALIGN_CENTER);
                    doc.add(img);
                } catch (Exception ex) {
                    LOG.warn("Falha ao renderizar barChart de exames", ex);
                }
            }

            // Heatmap dia-semana x semana-mes SEPARADO por severidade.
            // Ressalva dominio-auditor: misturar advertencia+rejeicao no mesmo
            // mapa suprime a informacao mais importante (onde caem as rejeicoes,
            // que forcam repeticao do lote). Agora renderizamos 2 matrizes:
            // REJEICAO (critico) + ADVERTENCIA (atencao).
            double[][] matrixRej = new double[7][5];
            double[][] matrixAdv = new double[7][5];
            long countRej = 0, countAdv = 0;
            for (WestgardViolation v : violations) {
                if (v.getQcRecord() == null || v.getQcRecord().getDate() == null) continue;
                LocalDate d = v.getQcRecord().getDate();
                int dow = d.getDayOfWeek().getValue() - 1; // 0..6 (Seg..Dom)
                int week = Math.min(4, (d.getDayOfMonth() - 1) / 7);
                String sev = v.getSeverity() == null ? "" : v.getSeverity().toUpperCase(Locale.ROOT);
                if (sev.startsWith("REJ") || "CRITICAL".equals(sev)) {
                    matrixRej[dow][week]++;
                    countRej++;
                } else {
                    matrixAdv[dow][week]++;
                    countAdv++;
                }
            }
            List<String> xLabels = List.of("Seg", "Ter", "Qua", "Qui", "Sex", "Sab", "Dom");
            List<String> yLabels = List.of("Sem 1", "Sem 2", "Sem 3", "Sem 4", "Sem 5");

            if (countRej > 0) {
                doc.add(ReportV2PdfTheme.section("Distribuicao temporal — Rejeicoes (" + countRej + ")"));
                try {
                    byte[] png = chartRenderer.renderHeatmap(matrixRej, xLabels, yLabels,
                        "Rejeicoes por dia-semana x semana-mes");
                    Image img = Image.getInstance(png);
                    img.scaleToFit(500F, 220F);
                    img.setAlignment(Element.ALIGN_CENTER);
                    doc.add(img);
                } catch (Exception ex) {
                    LOG.warn("Falha ao renderizar heatmap de rejeicoes", ex);
                }
            }
            if (countAdv > 0) {
                doc.add(ReportV2PdfTheme.section("Distribuicao temporal — Advertencias (" + countAdv + ")"));
                try {
                    byte[] png = chartRenderer.renderHeatmap(matrixAdv, xLabels, yLabels,
                        "Advertencias por dia-semana x semana-mes");
                    Image img = Image.getInstance(png);
                    img.scaleToFit(500F, 220F);
                    img.setAlignment(Element.ALIGN_CENTER);
                    doc.add(img);
                } catch (Exception ex) {
                    LOG.warn("Falha ao renderizar heatmap de advertencias", ex);
                }
            }
            if (countRej == 0 && countAdv == 0) {
                com.lowagie.text.Paragraph empty = new com.lowagie.text.Paragraph(
                    "Sem violacoes registradas no periodo selecionado.",
                    com.lowagie.text.FontFactory.getFont(
                        com.lowagie.text.FontFactory.HELVETICA, 10F,
                        com.lowagie.text.Font.ITALIC, ReportV2PdfTheme.MUTED));
                empty.setSpacingBefore(6F);
                doc.add(empty);
            }

            // Lista detalhada top 30
            doc.add(ReportV2PdfTheme.section("Ultimas violacoes"));
            PdfPTable detail = ReportV2PdfTheme.table(new float[] {1.2F, 1.3F, 2.2F, 1.3F, 1.5F, 3.5F});
            ReportV2PdfTheme.headerRow(detail, "Regra", "Severidade", "Exame", "Lote", "Data", "Descricao");
            alt = false;
            int limit = Math.min(30, violations.size());
            for (int i = 0; i < limit; i++) {
                WestgardViolation v = violations.get(i);
                ReportV2PdfTheme.bodyRow(detail, alt,
                    ReportV2PdfTheme.safe(v.getRule()),
                    ReportV2PdfTheme.safe(v.getSeverity()),
                    v.getQcRecord() == null ? "-" : ReportV2PdfTheme.safe(v.getQcRecord().getExamName()),
                    v.getQcRecord() == null ? "-" : ReportV2PdfTheme.safe(v.getQcRecord().getLotNumber()),
                    v.getQcRecord() == null ? "-" : ReportV2PdfTheme.formatDate(v.getQcRecord().getDate()),
                    ReportV2PdfTheme.safe(v.getDescription())
                );
                alt = !alt;
            }
            doc.add(detail);

            // Detalhamento por exame — uma secao completa por exame com violacoes
            if (rf.detailEachExam && !violations.isEmpty()) {
                doc.newPage();
                doc.add(ReportV2PdfTheme.section("Detalhamento por exame"));
                Paragraph intro = new Paragraph(
                    "Cada exame com violacoes no periodo aparece em sua propria secao com "
                    + "estatisticas, distribuicao por regra e historico cronologico completo.",
                    ReportV2PdfTheme.META_FONT);
                intro.setSpacingAfter(8F);
                doc.add(intro);

                Map<String, java.util.List<WestgardViolation>> byExamFull = violations.stream()
                    .filter(v -> v.getQcRecord() != null && v.getQcRecord().getExamName() != null)
                    .collect(Collectors.groupingBy(
                        v -> v.getQcRecord().getExamName(),
                        LinkedHashMap::new,
                        Collectors.toList()));
                List<Map.Entry<String, java.util.List<WestgardViolation>>> sortedExams =
                    byExamFull.entrySet().stream()
                        .sorted(Map.Entry.<String, java.util.List<WestgardViolation>>comparingByKey())
                        .collect(Collectors.toList());

                boolean first = true;
                for (Map.Entry<String, java.util.List<WestgardViolation>> e : sortedExams) {
                    if (!first) doc.newPage();
                    first = false;
                    renderExamWestgardDetail(doc, e.getKey(), e.getValue());
                }
            }

            // Comentario IA
            if (rf.includeAiCommentary) {
                doc.add(ReportV2PdfTheme.section("Analise executiva"));
                String structured = "Area: " + rf.area + "\nPeriodo: " + rf.periodLabel
                    + "\nTotal: " + violations.size() + "\nRejeicoes: " + rejeicoes;
                String commentary = aiCommentator.commentary(ReportCode.WESTGARD_DEEPDIVE, structured, ctx);
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
            throw new IllegalStateException("Falha ao gerar Westgard deep dive PDF", ex);
        }
    }

    private List<WestgardViolation> loadViolations(Resolved rf) {
        // T5: query janelada com JOIN FETCH — evita findAll()+filter que
        // carregava o universo inteiro. Area ja vem em lowercase (Resolved.area).
        List<WestgardViolation> base = violationRepository.findByAreaAndPeriod(rf.area, rf.start, rf.end);
        return base.stream()
            .filter(v -> rf.rules == null || rf.rules.isEmpty() || rf.rules.contains(v.getRule()))
            .filter(v -> rf.severity == null || rf.severity.isBlank()
                || (v.getSeverity() != null && v.getSeverity().equalsIgnoreCase(rf.severity)))
            .sorted(Comparator.comparing((WestgardViolation v) -> v.getQcRecord().getDate()).reversed())
            .collect(Collectors.toList());
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

    private Resolved resolve(ReportFilters filters) {
        Resolved r = new Resolved();
        r.area = filters.getString("area")
            .map(s -> s.trim().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new IllegalArgumentException("Filtro 'area' obrigatorio"));
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
                r.start = filters.getDate("dateFrom").orElseThrow(
                    () -> new IllegalArgumentException("dateFrom obrigatorio em date-range"));
                r.end = filters.getDate("dateTo").orElseThrow(
                    () -> new IllegalArgumentException("dateTo obrigatorio em date-range"));
                r.periodLabel = r.start.format(DateTimeFormatter.ISO_DATE) + " a "
                    + r.end.format(DateTimeFormatter.ISO_DATE);
            }
            default -> {
                YearMonth ym = YearMonth.now();
                r.start = ym.atDay(1); r.end = ym.atEndOfMonth();
                r.periodLabel = capitalize(ym.getMonth().getDisplayName(TextStyle.FULL, PT_BR)) + "/" + ym.getYear();
            }
        }
        r.rules = filters.getStringList("rules").orElse(null);
        r.severity = filters.getString("severity").orElse(null);
        r.detailEachExam = filters.getBoolean("detailEachExam").orElse(true);
        return r;
    }

    private static String capitalize(String v) {
        if (v == null || v.isEmpty()) return "";
        return v.substring(0, 1).toUpperCase(PT_BR) + v.substring(1);
    }

    static final class Resolved {
        String area;
        LocalDate start;
        LocalDate end;
        String periodLabel;
        List<String> rules;
        String severity;
        boolean includeAiCommentary;
        boolean detailEachExam;
    }

    /**
     * Renderiza UMA secao por exame com violacoes Westgard. Inclui:
     * - Cabecalho com nome do exame
     * - Cards: total violacoes, rejeicoes, advertencias, taxa
     * - Distribuicao por regra (top regras com contagem)
     * - Distribuicao temporal compacta (ultimas 12 datas)
     * - Tabela cronologica completa de violacoes
     */
    private void renderExamWestgardDetail(Document doc, String examName,
            java.util.List<WestgardViolation> viols) throws DocumentException {
        Paragraph h = new Paragraph(examName,
            com.lowagie.text.FontFactory.getFont(
                com.lowagie.text.FontFactory.HELVETICA_BOLD, 14, ReportV2PdfTheme.BRAND_DARK));
        h.setSpacingBefore(4F); h.setSpacingAfter(6F);
        doc.add(h);

        long rej = viols.stream().filter(v -> {
            String s = v.getSeverity() == null ? "" : v.getSeverity().toUpperCase(Locale.ROOT);
            return s.startsWith("REJ") || "CRITICAL".equals(s);
        }).count();
        long adv = viols.size() - rej;

        PdfPTable cards = new PdfPTable(new float[] {1, 1, 1, 1});
        cards.setWidthPercentage(100F); cards.setSpacingAfter(8F);
        cards.addCell(summaryCell("Total violacoes", String.valueOf(viols.size()), ReportV2PdfTheme.BRAND_PRIMARY));
        cards.addCell(summaryCell("Rejeicoes", String.valueOf(rej), ReportV2PdfTheme.STATUS_REPROVADO));
        cards.addCell(summaryCell("Advertencias", String.valueOf(adv), ReportV2PdfTheme.STATUS_ALERTA));
        java.util.Set<java.time.LocalDate> distinctDates = viols.stream()
            .map(v -> v.getQcRecord() == null ? null : v.getQcRecord().getDate())
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        cards.addCell(summaryCell("Dias afetados", String.valueOf(distinctDates.size()), ReportV2PdfTheme.MUTED));
        doc.add(cards);

        // Distribuicao por regra
        Map<String, Long> byRule = viols.stream()
            .filter(v -> v.getRule() != null)
            .collect(Collectors.groupingBy(WestgardViolation::getRule, Collectors.counting()));
        if (!byRule.isEmpty()) {
            doc.add(ReportV2PdfTheme.subsection("Regras violadas"));
            String rulesStr = byRule.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.joining(", "));
            Paragraph rp = new Paragraph(rulesStr, ReportV2PdfTheme.BODY_FONT);
            rp.setSpacingAfter(8F);
            doc.add(rp);
        }

        // Tabela cronologica
        doc.add(ReportV2PdfTheme.subsection("Historico de violacoes"));
        java.util.List<WestgardViolation> sorted = viols.stream()
            .sorted(java.util.Comparator.<WestgardViolation, java.time.LocalDate>comparing(
                v -> v.getQcRecord() == null ? null : v.getQcRecord().getDate(),
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
            .collect(Collectors.toList());
        PdfPTable t = ReportV2PdfTheme.table(new float[] {1F, 1F, 1.3F, 1.4F, 1F, 3F});
        ReportV2PdfTheme.headerRow(t, "Data", "Regra", "Severidade", "Lote", "Nivel", "Descricao");
        boolean alt = false;
        for (WestgardViolation v : sorted) {
            ReportV2PdfTheme.bodyRow(t, alt,
                v.getQcRecord() == null ? "—" : ReportV2PdfTheme.formatDate(v.getQcRecord().getDate()),
                ReportV2PdfTheme.safe(v.getRule()),
                ReportV2PdfTheme.safe(v.getSeverity()),
                v.getQcRecord() == null ? "—" : ReportV2PdfTheme.safe(v.getQcRecord().getLotNumber()),
                v.getQcRecord() == null ? "—" : ReportV2PdfTheme.safe(v.getQcRecord().getLevel()),
                truncate(ReportV2PdfTheme.safe(v.getDescription()), 80));
            alt = !alt;
        }
        doc.add(t);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "—";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
