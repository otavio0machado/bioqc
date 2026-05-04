package com.bioqc.service.reports.v2.generator.impl;

import com.bioqc.entity.LabSettings;
import com.bioqc.entity.PostCalibrationRecord;
import com.bioqc.repository.PostCalibrationRecordRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CalibracaoPrePostGenerator implements ReportGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(CalibracaoPrePostGenerator.class);
    private static final Locale PT_BR = ReportV2PdfTheme.PT_BR;

    private final PostCalibrationRecordRepository repository;
    private final ReportNumberingService reportNumberingService;
    private final ChartRenderer chartRenderer;
    private final LabHeaderRenderer headerRenderer;
    private final LabSettingsService labSettingsService;
    private final ReportAiCommentator aiCommentator;
    private final com.bioqc.config.ReportsV2Properties properties;

    public CalibracaoPrePostGenerator(
        PostCalibrationRecordRepository repository,
        ReportNumberingService reportNumberingService,
        ChartRenderer chartRenderer,
        LabHeaderRenderer headerRenderer,
        LabSettingsService labSettingsService,
        ReportAiCommentator aiCommentator,
        com.bioqc.config.ReportsV2Properties properties
    ) {
        this.repository = repository;
        this.reportNumberingService = reportNumberingService;
        this.chartRenderer = chartRenderer;
        this.headerRenderer = headerRenderer;
        this.labSettingsService = labSettingsService;
        this.aiCommentator = aiCommentator;
        this.properties = properties;
    }

    /**
     * Classifica o efeito de uma calibracao segundo a tolerancia configurada em
     * {@code reports.v2.calibration-effective-delta-tolerance} (default 0.5 pp).
     * Variacoes dentro da faixa de ruido viram "SEM EFEITO" em vez de
     * classificacoes espurias.
     */
    private String classifyCalibrationDelta(double delta) {
        double tol = properties == null ? 0.5 : properties.getCalibrationEffectiveDeltaTolerance();
        if (delta <= -tol) return "EFICAZ";
        if (delta >= tol) return "PIOROU";
        return "SEM EFEITO";
    }

    @Override
    public ReportDefinition definition() {
        return ReportDefinitionRegistry.CALIBRACAO_PREPOST_DEFINITION;
    }

    @Override
    @Transactional
    public ReportArtifact generate(ReportFilters filters, GenerationContext ctx) {
        Resolved rf = resolve(filters);
        String reportNumber = reportNumberingService.reserveNextNumber();
        byte[] pdfBytes = renderPdf(rf, ctx, reportNumber);
        String sha256 = reportNumberingService.sha256Hex(pdfBytes);
        reportNumberingService.registerGeneration(reportNumber, "calibracao", "PDF", rf.periodLabel,
            pdfBytes, ctx == null ? null : ctx.userId());
        return new ReportArtifact(pdfBytes, "application/pdf", reportNumber + ".pdf", 0,
            pdfBytes.length, reportNumber, sha256, rf.periodLabel);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportPreview preview(ReportFilters filters, GenerationContext ctx) {
        Resolved rf = resolve(filters);
        List<PostCalibrationRecord> records = repository.findByQcRecordAreaAndDateRange(
            rf.area == null ? "bioquimica" : rf.area, rf.start, rf.end);
        StringBuilder html = new StringBuilder();
        html.append("<section><h1 style=\"color:#14532d\">Calibracao Pre/Pos</h1>");
        html.append("<p>Registros: <strong>").append(records.size()).append("</strong></p></section>");
        return new ReportPreview(html.toString(),
            records.isEmpty() ? List.of("Nenhuma calibracao no periodo.") : List.of(), rf.periodLabel);
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

            List<PostCalibrationRecord> records = repository.findByQcRecordAreaAndDateRange(
                rf.area == null ? "bioquimica" : rf.area, rf.start, rf.end);
            // Tolerancia configuravel (default 0.5pp) para classificar efeito.
            long eficazes = records.stream().filter(r -> {
                if (r.getOriginalCv() == null || r.getPostCalibrationCv() == null) return false;
                double delta = r.getPostCalibrationCv() - r.getOriginalCv();
                return "EFICAZ".equals(classifyCalibrationDelta(delta));
            }).count();
            long semEfeito = records.size() - eficazes;

            doc.add(ReportV2PdfTheme.section("Resumo"));
            PdfPTable cards = new PdfPTable(new float[] {1, 1, 1});
            cards.setWidthPercentage(100F); cards.setSpacingAfter(6F);
            cards.addCell(card("Total", String.valueOf(records.size()), ReportV2PdfTheme.BRAND_PRIMARY));
            cards.addCell(card("Eficazes", String.valueOf(eficazes), ReportV2PdfTheme.STATUS_APROVADO));
            cards.addCell(card("Sem efeito/pioraram", String.valueOf(semEfeito), ReportV2PdfTheme.STATUS_ALERTA));
            doc.add(cards);

            if (!records.isEmpty()) {
                doc.add(ReportV2PdfTheme.section("Detalhe antes/depois"));
                PdfPTable t = ReportV2PdfTheme.table(new float[] {2.6F, 1.4F, 1.4F, 1.4F, 1.2F, 1.5F});
                ReportV2PdfTheme.headerRow(t, "Exame", "CV antes", "CV depois", "Delta%", "Status", "Data");
                boolean alt = false;
                Map<String, Number> deltaChart = new LinkedHashMap<>();
                for (PostCalibrationRecord r : records) {
                    double oCv = r.getOriginalCv() == null ? 0 : r.getOriginalCv();
                    double pCv = r.getPostCalibrationCv() == null ? 0 : r.getPostCalibrationCv();
                    double delta = pCv - oCv;
                    String st = classifyCalibrationDelta(delta);
                    ReportV2PdfTheme.bodyRow(t, alt,
                        ReportV2PdfTheme.safe(r.getExamName()),
                        ReportV2PdfTheme.formatDecimal(oCv),
                        ReportV2PdfTheme.formatDecimal(pCv),
                        String.format(PT_BR, "%+.2f", delta),
                        st,
                        ReportV2PdfTheme.formatDate(r.getDate()));
                    alt = !alt;
                    deltaChart.put(ReportV2PdfTheme.safe(r.getExamName()), delta);
                }
                doc.add(t);
                if (!deltaChart.isEmpty()) {
                    try {
                        byte[] png = chartRenderer.renderBarChart(deltaChart,
                            "Delta CV por exame (negativo = eficaz)", "Exame", "Delta CV%");
                        Image img = Image.getInstance(png);
                        img.scaleToFit(500F, 220F);
                        img.setAlignment(Element.ALIGN_CENTER);
                        doc.add(img);
                    } catch (Exception ex) {
                        LOG.warn("Falha chart calibracao", ex);
                    }
                }
            }

            // Detalhamento por exame — uma secao completa por exame, agrupando suas calibracoes
            if (rf.detailEachExam && !records.isEmpty()) {
                doc.newPage();
                doc.add(ReportV2PdfTheme.section("Detalhamento por exame"));
                Paragraph intro = new Paragraph(
                    "Cada exame abaixo aparece em sua propria secao com todas as calibracoes "
                    + "do periodo (antes/depois, delta CV, classificacao).",
                    ReportV2PdfTheme.META_FONT);
                intro.setSpacingAfter(8F);
                doc.add(intro);

                java.util.Map<String, java.util.List<com.bioqc.entity.PostCalibrationRecord>> byExam =
                    records.stream()
                        .filter(r -> r.getExamName() != null && !r.getExamName().isBlank())
                        .collect(java.util.stream.Collectors.groupingBy(
                            com.bioqc.entity.PostCalibrationRecord::getExamName,
                            java.util.LinkedHashMap::new,
                            java.util.stream.Collectors.toList()));
                java.util.List<java.util.Map.Entry<String, java.util.List<com.bioqc.entity.PostCalibrationRecord>>> sorted =
                    byExam.entrySet().stream()
                        .sorted(java.util.Map.Entry.comparingByKey())
                        .collect(java.util.stream.Collectors.toList());

                boolean first = true;
                for (java.util.Map.Entry<String, java.util.List<com.bioqc.entity.PostCalibrationRecord>> e : sorted) {
                    if (!first) doc.newPage();
                    first = false;
                    renderExamCalibrationDetail(doc, e.getKey(), e.getValue());
                }
            }

            if (rf.includeAiCommentary) {
                doc.add(ReportV2PdfTheme.section("Analise executiva"));
                String structured = "Periodo: " + rf.periodLabel + "\nTotal calibracoes: " + records.size()
                    + "\nEficazes: " + eficazes + "\nSem efeito ou pioraram: " + semEfeito;
                String commentary = aiCommentator.commentary(ReportCode.CALIBRACAO_PREPOST, structured, ctx);
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
            throw new IllegalStateException("Falha ao gerar PDF calibracao", ex);
        }
    }

    private Resolved resolve(ReportFilters filters) {
        Resolved r = new Resolved();
        // Normalizar area p/ lowercase — repository evita LOWER(:area) (PG infere bytea em null).
        r.area = filters.getString("area")
            .map(s -> s.trim().toLowerCase(java.util.Locale.ROOT))
            .filter(s -> !s.isEmpty())
            .orElse("bioquimica");
        r.equipment = filters.getString("equipment")
            .map(s -> s.trim().toLowerCase(java.util.Locale.ROOT))
            .filter(s -> !s.isEmpty())
            .orElse(null);
        r.includeAiCommentary = filters.getBoolean("includeAiCommentary").orElse(false);
        r.detailEachExam = filters.getBoolean("detailEachExam").orElse(true);
        String periodType = filters.getString("periodType")
            .map(s -> s.trim().toLowerCase(Locale.ROOT)).orElse("current-month");
        LocalDate today = LocalDate.now();
        switch (periodType) {
            case "year" -> {
                int y = filters.getInteger("year").orElse(today.getYear());
                r.start = LocalDate.of(y, 1, 1);
                r.end = LocalDate.of(y, 12, 31);
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

    private PdfPCell card(String label, String value, java.awt.Color color) {
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

    private static String capitalize(String v) {
        if (v == null || v.isEmpty()) return "";
        return v.substring(0, 1).toUpperCase(PT_BR) + v.substring(1);
    }

    static final class Resolved {
        String area;
        String equipment;
        LocalDate start;
        LocalDate end;
        String periodLabel;
        boolean includeAiCommentary;
        boolean detailEachExam;
    }

    /**
     * Renderiza UMA secao por exame com TODAS suas calibracoes do periodo.
     */
    private void renderExamCalibrationDetail(Document doc, String examName,
            java.util.List<com.bioqc.entity.PostCalibrationRecord> events) throws DocumentException {
        Paragraph h = new Paragraph(examName,
            com.lowagie.text.FontFactory.getFont(
                com.lowagie.text.FontFactory.HELVETICA_BOLD, 14, ReportV2PdfTheme.BRAND_DARK));
        h.setSpacingBefore(4F); h.setSpacingAfter(6F);
        doc.add(h);

        // Stats deste exame
        long eficazes = events.stream().filter(r -> {
            if (r.getOriginalCv() == null || r.getPostCalibrationCv() == null) return false;
            return "EFICAZ".equals(classifyCalibrationDelta(r.getPostCalibrationCv() - r.getOriginalCv()));
        }).count();
        long pioraram = events.stream().filter(r -> {
            if (r.getOriginalCv() == null || r.getPostCalibrationCv() == null) return false;
            return "PIOROU".equals(classifyCalibrationDelta(r.getPostCalibrationCv() - r.getOriginalCv()));
        }).count();
        long semEf = events.size() - eficazes - pioraram;

        PdfPTable cards = new PdfPTable(new float[] {1, 1, 1, 1});
        cards.setWidthPercentage(100F);
        cards.setSpacingAfter(8F);
        cards.addCell(card("Total no periodo", String.valueOf(events.size()), ReportV2PdfTheme.BRAND_PRIMARY));
        cards.addCell(card("Eficazes", String.valueOf(eficazes), ReportV2PdfTheme.STATUS_APROVADO));
        cards.addCell(card("Sem efeito", String.valueOf(semEf), ReportV2PdfTheme.MUTED));
        cards.addCell(card("Pioraram", String.valueOf(pioraram), ReportV2PdfTheme.STATUS_REPROVADO));
        doc.add(cards);

        // Tabela cronologica
        doc.add(ReportV2PdfTheme.subsection("Eventos de calibracao"));
        PdfPTable t = ReportV2PdfTheme.table(new float[] {1.1F, 1.2F, 1.2F, 1.2F, 1.2F, 1F, 1.3F, 1.6F, 2.5F});
        ReportV2PdfTheme.headerRow(t, "Data", "CV antes", "CV depois", "Valor antes", "Valor depois",
            "Delta CV", "Status", "Analista", "Notas");
        boolean alt = false;
        java.util.List<com.bioqc.entity.PostCalibrationRecord> sorted = events.stream()
            .sorted(java.util.Comparator.comparing(
                com.bioqc.entity.PostCalibrationRecord::getDate,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
            .collect(java.util.stream.Collectors.toList());
        for (com.bioqc.entity.PostCalibrationRecord r : sorted) {
            double oCv = r.getOriginalCv() == null ? 0 : r.getOriginalCv();
            double pCv = r.getPostCalibrationCv() == null ? 0 : r.getPostCalibrationCv();
            double delta = pCv - oCv;
            String st = r.getOriginalCv() == null || r.getPostCalibrationCv() == null
                ? "—" : classifyCalibrationDelta(delta);
            ReportV2PdfTheme.bodyRow(t, alt,
                ReportV2PdfTheme.formatDate(r.getDate()),
                ReportV2PdfTheme.formatDecimal(oCv),
                ReportV2PdfTheme.formatDecimal(pCv),
                ReportV2PdfTheme.formatDecimal(r.getOriginalValue()),
                ReportV2PdfTheme.formatDecimal(r.getPostCalibrationValue()),
                String.format(PT_BR, "%+.2f", delta),
                st,
                ReportV2PdfTheme.safe(r.getAnalyst()),
                truncate(ReportV2PdfTheme.safe(r.getNotes()), 60));
            alt = !alt;
        }
        doc.add(t);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "—";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
