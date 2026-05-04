package com.bioqc.service.reports.v2.generator.impl;

import com.bioqc.entity.LabSettings;
import com.bioqc.entity.MaintenanceRecord;
import com.bioqc.repository.MaintenanceRecordRepository;
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
import java.time.temporal.ChronoUnit;
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

@Component
public class ManutencaoKpiGenerator implements ReportGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(ManutencaoKpiGenerator.class);
    private static final Locale PT_BR = ReportV2PdfTheme.PT_BR;

    private final MaintenanceRecordRepository repository;
    private final ReportNumberingService reportNumberingService;
    private final ChartRenderer chartRenderer;
    private final LabHeaderRenderer headerRenderer;
    private final LabSettingsService labSettingsService;
    private final ReportAiCommentator aiCommentator;

    public ManutencaoKpiGenerator(
        MaintenanceRecordRepository repository,
        ReportNumberingService reportNumberingService,
        ChartRenderer chartRenderer,
        LabHeaderRenderer headerRenderer,
        LabSettingsService labSettingsService,
        ReportAiCommentator aiCommentator
    ) {
        this.repository = repository;
        this.reportNumberingService = reportNumberingService;
        this.chartRenderer = chartRenderer;
        this.headerRenderer = headerRenderer;
        this.labSettingsService = labSettingsService;
        this.aiCommentator = aiCommentator;
    }

    @Override
    public ReportDefinition definition() {
        return ReportDefinitionRegistry.MANUTENCAO_KPI_DEFINITION;
    }

    @Override
    @Transactional
    public ReportArtifact generate(ReportFilters filters, GenerationContext ctx) {
        Resolved rf = resolve(filters);
        String reportNumber = reportNumberingService.reserveNextNumber();
        byte[] pdfBytes = renderPdf(rf, ctx, reportNumber);
        String sha256 = reportNumberingService.sha256Hex(pdfBytes);
        reportNumberingService.registerGeneration(reportNumber, "manutencao", "PDF", rf.periodLabel,
            pdfBytes, ctx == null ? null : ctx.userId());
        return new ReportArtifact(pdfBytes, "application/pdf", reportNumber + ".pdf", 0,
            pdfBytes.length, reportNumber, sha256, rf.periodLabel);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportPreview preview(ReportFilters filters, GenerationContext ctx) {
        Resolved rf = resolve(filters);
        List<MaintenanceRecord> records = repository.findInPeriod(rf.start, rf.end, rf.equipment, rf.type);
        StringBuilder html = new StringBuilder();
        html.append("<section><h1 style=\"color:#14532d\">KPIs de Manutencao</h1>");
        html.append("<p>Periodo: ").append(rf.periodLabel).append("</p>");
        html.append("<p>Manutencoes no periodo: <strong>").append(records.size()).append("</strong></p>");
        html.append("</section>");
        return new ReportPreview(html.toString(),
            records.isEmpty() ? List.of("Nenhuma manutencao no periodo.") : List.of(), rf.periodLabel);
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

            List<MaintenanceRecord> records = repository.findInPeriod(rf.start, rf.end, rf.equipment, rf.type);
            long preventivas = records.stream().filter(r -> "Preventiva".equalsIgnoreCase(r.getType())).count();
            long corretivas = records.stream().filter(r -> "Corretiva".equalsIgnoreCase(r.getType())).count();
            long calibracoes = records.stream().filter(r -> "Calibracao".equalsIgnoreCase(r.getType()) || "Calibração".equalsIgnoreCase(r.getType())).count();

            doc.add(ReportV2PdfTheme.section("Resumo"));
            PdfPTable cards = new PdfPTable(new float[] {1, 1, 1, 1});
            cards.setWidthPercentage(100F); cards.setSpacingAfter(6F);
            cards.addCell(card("Total", String.valueOf(records.size()), ReportV2PdfTheme.BRAND_PRIMARY));
            cards.addCell(card("Preventivas", String.valueOf(preventivas), ReportV2PdfTheme.STATUS_APROVADO));
            cards.addCell(card("Corretivas", String.valueOf(corretivas), ReportV2PdfTheme.STATUS_REPROVADO));
            cards.addCell(card("Calibracoes", String.valueOf(calibracoes), ReportV2PdfTheme.BRAND_DARK));
            doc.add(cards);

            // Por equipamento
            Map<String, Long> byEquip = records.stream().collect(Collectors.groupingBy(
                r -> r.getEquipment() == null ? "-" : r.getEquipment(),
                LinkedHashMap::new, Collectors.counting()));
            if (!byEquip.isEmpty()) {
                doc.add(ReportV2PdfTheme.section("Manutencoes por equipamento"));
                Map<String, Number> ds = new LinkedHashMap<>();
                byEquip.forEach(ds::put);
                try {
                    byte[] png = chartRenderer.renderBarChart(ds, "Por equipamento", "Equipamento", "Ocorrencias");
                    Image img = Image.getInstance(png);
                    img.scaleToFit(500F, 220F);
                    img.setAlignment(Element.ALIGN_CENTER);
                    doc.add(img);
                } catch (Exception ex) {
                    LOG.warn("Falha bar chart manutencao", ex);
                }
                PdfPTable t = ReportV2PdfTheme.table(new float[] {3F, 1.5F, 1.8F});
                ReportV2PdfTheme.headerRow(t, "Equipamento", "Manutencoes", "MTBF (dias)");
                boolean alt = false;
                for (Map.Entry<String, Long> e : byEquip.entrySet()) {
                    List<MaintenanceRecord> equipRecs = records.stream()
                        .filter(r -> e.getKey().equals(r.getEquipment()))
                        .sorted(Comparator.comparing(MaintenanceRecord::getDate))
                        .collect(Collectors.toList());
                    String mtbf = computeMtbf(equipRecs);
                    ReportV2PdfTheme.bodyRow(t, alt, e.getKey(), String.valueOf(e.getValue()), mtbf);
                    alt = !alt;
                }
                doc.add(t);
            }

            // Proximas manutencoes — T5: query janelada (substitui findAll+filter)
            LocalDate today = LocalDate.now();
            List<MaintenanceRecord> upcoming = repository.findUpcoming(today, today.plusDays(90));
            if (!upcoming.isEmpty()) {
                doc.add(ReportV2PdfTheme.section("Proximas manutencoes (90 dias)"));
                PdfPTable t = ReportV2PdfTheme.table(new float[] {3F, 1.6F, 1.5F, 1.5F});
                ReportV2PdfTheme.headerRow(t, "Equipamento", "Tipo", "Agendada", "Ultima");
                boolean alt = false;
                for (MaintenanceRecord r : upcoming) {
                    ReportV2PdfTheme.bodyRow(t, alt,
                        ReportV2PdfTheme.safe(r.getEquipment()),
                        ReportV2PdfTheme.safe(r.getType()),
                        ReportV2PdfTheme.formatDate(r.getNextDate()),
                        ReportV2PdfTheme.formatDate(r.getDate())
                    );
                    alt = !alt;
                }
                doc.add(t);
            }

            // Atrasadas — T5: query janelada
            List<MaintenanceRecord> overdue = repository.findOverdue(today);
            if (!overdue.isEmpty()) {
                doc.add(ReportV2PdfTheme.section("ATENCAO: Manutencoes atrasadas"));
                PdfPTable wrap = new PdfPTable(1);
                wrap.setWidthPercentage(100F);
                wrap.addCell(ReportV2PdfTheme.calloutBox(
                    overdue.size() + " manutencao(oes) atrasada(s). Priorizar agendamento.",
                    ReportV2PdfTheme.ALERT_BG, ReportV2PdfTheme.STATUS_REPROVADO));
                doc.add(wrap);
            }

            // Detalhamento por equipamento — uma secao completa por equipamento
            if (rf.detailEachEquipment && !records.isEmpty()) {
                doc.newPage();
                doc.add(ReportV2PdfTheme.section("Detalhamento por equipamento"));
                Paragraph intro = new Paragraph(
                    "Cada equipamento abaixo aparece em sua propria secao com historico cronologico completo, "
                    + "MTBF, distribuicao por tipo, tecnicos envolvidos e proxima manutencao agendada.",
                    ReportV2PdfTheme.META_FONT);
                intro.setSpacingAfter(8F);
                doc.add(intro);

                Map<String, List<MaintenanceRecord>> byEquipment = records.stream()
                    .filter(r -> r.getEquipment() != null && !r.getEquipment().isBlank())
                    .collect(Collectors.groupingBy(MaintenanceRecord::getEquipment, LinkedHashMap::new, Collectors.toList()));
                List<Map.Entry<String, List<MaintenanceRecord>>> sorted = byEquipment.entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getKey().toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());

                boolean first = true;
                for (Map.Entry<String, List<MaintenanceRecord>> entry : sorted) {
                    if (!first) doc.newPage();
                    first = false;
                    renderEquipmentDetail(doc, entry.getKey(), entry.getValue(), today);
                }
            }

            if (rf.includeAiCommentary) {
                doc.add(ReportV2PdfTheme.section("Analise executiva"));
                String structured = "Periodo: " + rf.periodLabel + "\nTotal: " + records.size()
                    + "\nPreventivas: " + preventivas + "\nCorretivas: " + corretivas
                    + "\nCalibracoes: " + calibracoes + "\nAtrasadas: " + overdue.size();
                String commentary = aiCommentator.commentary(ReportCode.MANUTENCAO_KPI, structured, ctx);
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
            throw new IllegalStateException("Falha ao gerar PDF manutencao", ex);
        }
    }

    /**
     * Renderiza UMA secao por equipamento. Inclui:
     * - Cabecalho com nome do equipamento
     * - Cards: total no periodo, preventivas, corretivas, calibracoes, MTBF
     * - Proxima manutencao agendada (se existe)
     * - Atrasadas neste equipamento (se existe)
     * - Tecnicos envolvidos (top 5)
     * - Tabela cronologica de todas as manutencoes
     */
    private void renderEquipmentDetail(Document doc, String equipment, List<MaintenanceRecord> recs, LocalDate today)
            throws DocumentException {
        // Cabecalho
        Paragraph h = new Paragraph(equipment,
            com.lowagie.text.FontFactory.getFont(
                com.lowagie.text.FontFactory.HELVETICA_BOLD, 14, ReportV2PdfTheme.BRAND_DARK));
        h.setSpacingBefore(4F); h.setSpacingAfter(6F);
        doc.add(h);

        // Stats deste equipamento
        long prev = recs.stream().filter(r -> isPreventiva(r.getType())).count();
        long corr = recs.stream().filter(r -> isCorretiva(r.getType())).count();
        long cal = recs.stream().filter(r -> isCalibracao(r.getType())).count();

        // Sort por data ASC para MTBF correto
        List<MaintenanceRecord> chrono = recs.stream()
            .sorted(Comparator.comparing(MaintenanceRecord::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
        String mtbf = computeMtbf(chrono);

        PdfPTable cards = new PdfPTable(new float[] {1, 1, 1, 1, 1});
        cards.setWidthPercentage(100F);
        cards.setSpacingAfter(6F);
        cards.addCell(card("Total", String.valueOf(recs.size()), ReportV2PdfTheme.BRAND_PRIMARY));
        cards.addCell(card("Preventivas", String.valueOf(prev), ReportV2PdfTheme.STATUS_APROVADO));
        cards.addCell(card("Corretivas", String.valueOf(corr), ReportV2PdfTheme.STATUS_REPROVADO));
        cards.addCell(card("Calibracoes", String.valueOf(cal), ReportV2PdfTheme.BRAND_PRIMARY));
        cards.addCell(card("MTBF (dias)", mtbf, ReportV2PdfTheme.MUTED));
        doc.add(cards);

        // Proxima e atrasadas
        java.util.Optional<MaintenanceRecord> nextScheduled = recs.stream()
            .filter(r -> r.getNextDate() != null && !r.getNextDate().isBefore(today))
            .min(Comparator.comparing(MaintenanceRecord::getNextDate));
        java.util.List<MaintenanceRecord> overdue = recs.stream()
            .filter(r -> r.getNextDate() != null && r.getNextDate().isBefore(today))
            .sorted(Comparator.comparing(MaintenanceRecord::getNextDate))
            .collect(Collectors.toList());
        Paragraph schedule = new Paragraph();
        if (nextScheduled.isPresent()) {
            LocalDate nd = nextScheduled.get().getNextDate();
            long daysUntil = ChronoUnit.DAYS.between(today, nd);
            schedule.add(new com.lowagie.text.Chunk("Proxima manutencao: ", ReportV2PdfTheme.BODY_BOLD_FONT));
            schedule.add(new com.lowagie.text.Chunk(
                ReportV2PdfTheme.formatDate(nd) + " (em " + daysUntil + " dias)",
                com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 9,
                    daysUntil <= 7 ? ReportV2PdfTheme.STATUS_ALERTA : ReportV2PdfTheme.STATUS_APROVADO)));
        } else {
            schedule.add(new com.lowagie.text.Chunk("Sem proxima manutencao agendada.",
                ReportV2PdfTheme.META_FONT));
        }
        if (!overdue.isEmpty()) {
            schedule.add(com.lowagie.text.Chunk.NEWLINE);
            schedule.add(new com.lowagie.text.Chunk(
                "ATENCAO: " + overdue.size() + " manutencao(oes) atrasada(s)",
                com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 9,
                    ReportV2PdfTheme.STATUS_REPROVADO)));
        }
        schedule.setSpacingAfter(8F);
        doc.add(schedule);

        // Tecnicos envolvidos (top 5)
        Map<String, Long> tecnicos = recs.stream()
            .filter(r -> r.getTechnician() != null && !r.getTechnician().isBlank())
            .collect(Collectors.groupingBy(MaintenanceRecord::getTechnician, Collectors.counting()));
        if (!tecnicos.isEmpty()) {
            doc.add(ReportV2PdfTheme.subsection("Tecnicos envolvidos"));
            String techStr = tecnicos.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.joining(", "));
            Paragraph tp = new Paragraph(techStr, ReportV2PdfTheme.BODY_FONT);
            tp.setSpacingAfter(8F);
            doc.add(tp);
        }

        // Tabela cronologica completa (mais recentes primeiro)
        doc.add(ReportV2PdfTheme.subsection("Historico de manutencoes"));
        List<MaintenanceRecord> sortedDesc = recs.stream()
            .sorted(Comparator.comparing(MaintenanceRecord::getDate,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());
        PdfPTable t = ReportV2PdfTheme.table(new float[] {1.1F, 1.4F, 1.6F, 1.2F, 3F});
        ReportV2PdfTheme.headerRow(t, "Data", "Tipo", "Tecnico", "Proxima", "Observacoes");
        boolean alt = false;
        for (MaintenanceRecord r : sortedDesc) {
            ReportV2PdfTheme.bodyRow(t, alt,
                ReportV2PdfTheme.formatDate(r.getDate()),
                ReportV2PdfTheme.safe(r.getType()),
                ReportV2PdfTheme.safe(r.getTechnician()),
                ReportV2PdfTheme.formatDate(r.getNextDate()),
                truncate(ReportV2PdfTheme.safe(r.getNotes()), 80));
            alt = !alt;
        }
        doc.add(t);
    }

    private static boolean isPreventiva(String t) {
        return t != null && t.toLowerCase(Locale.ROOT).contains("prevent");
    }

    private static boolean isCorretiva(String t) {
        return t != null && t.toLowerCase(Locale.ROOT).contains("corret");
    }

    private static boolean isCalibracao(String t) {
        return t != null && t.toLowerCase(Locale.ROOT).contains("calib");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "—";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private String computeMtbf(List<MaintenanceRecord> recs) {
        if (recs.size() < 2) return "-";
        long totalDays = 0;
        int intervals = 0;
        for (int i = 1; i < recs.size(); i++) {
            LocalDate prev = recs.get(i - 1).getDate();
            LocalDate cur = recs.get(i).getDate();
            if (prev == null || cur == null) continue;
            totalDays += ChronoUnit.DAYS.between(prev, cur);
            intervals++;
        }
        if (intervals == 0) return "-";
        return String.format(PT_BR, "%.1f", (double) totalDays / intervals);
    }

    private Resolved resolve(ReportFilters filters) {
        Resolved r = new Resolved();
        // Normalizar p/ lowercase — repository evita LOWER(:param) pra nao cair
        // no bug PostgreSQL (infere bytea para param null em LOWER).
        r.equipment = filters.getString("equipment")
            .map(s -> s.trim().toLowerCase(Locale.ROOT))
            .filter(s -> !s.isEmpty())
            .orElse(null);
        r.type = filters.getString("maintenanceType")
            .map(s -> s.trim().toLowerCase(Locale.ROOT))
            .filter(s -> !s.isEmpty())
            .orElse(null);
        r.includeAiCommentary = filters.getBoolean("includeAiCommentary").orElse(false);
        r.detailEachEquipment = filters.getBoolean("detailEachEquipment").orElse(true);
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
        LocalDate start;
        LocalDate end;
        String periodLabel;
        String equipment;
        String type;
        boolean includeAiCommentary;
        boolean detailEachEquipment;
    }
}
