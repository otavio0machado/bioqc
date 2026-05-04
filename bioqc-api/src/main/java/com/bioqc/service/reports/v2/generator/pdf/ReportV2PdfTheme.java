package com.bioqc.service.reports.v2.generator.pdf;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import java.awt.Color;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Tema visual compartilhado pelos generators Reports V2. Evita duplicar
 * constantes de fonte, cor e helpers de tabela em cada implementacao.
 *
 * <p>Nao toca no V1 (PdfReportService) — ponto de extensao novo.
 */
public final class ReportV2PdfTheme {

    public static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    // Paleta branding
    public static final Color BRAND_PRIMARY = new Color(22, 101, 52);
    public static final Color BRAND_DARK = new Color(20, 83, 45);
    public static final Color BRAND_LIGHT = new Color(187, 247, 208);
    public static final Color MUTED = new Color(75, 85, 99);
    public static final Color ROW_ALT = new Color(244, 247, 245);
    public static final Color BORDER = new Color(210, 214, 218);

    // Status
    public static final Color STATUS_APROVADO = new Color(22, 163, 74);
    public static final Color STATUS_ALERTA = new Color(234, 179, 8);
    public static final Color STATUS_REPROVADO = new Color(220, 38, 38);
    public static final Color WARN_BG = new Color(254, 243, 199);
    public static final Color ALERT_BG = new Color(254, 226, 226);

    // Fontes
    public static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BRAND_DARK);
    public static final Font SUBSECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND_PRIMARY);
    public static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    public static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
    public static final Font BODY_BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
    public static final Font META_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED);
    public static final Font AI_FONT = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, BRAND_DARK);

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ReportV2PdfTheme() {}

    public static PdfPTable table(float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100F);
        t.setSpacingBefore(6F);
        t.setSpacingAfter(8F);
        return t;
    }

    public static void headerRow(PdfPTable t, String... values) {
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v == null ? "" : v, HEADER_FONT));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(5F);
            cell.setBackgroundColor(BRAND_PRIMARY);
            cell.setBorderColor(BORDER);
            t.addCell(cell);
        }
    }

    public static void bodyRow(PdfPTable t, boolean alt, String... values) {
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v == null ? "" : v, BODY_FONT));
            cell.setPadding(4F);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setBorderColor(BORDER);
            cell.setBackgroundColor(alt ? ROW_ALT : Color.WHITE);
            t.addCell(cell);
        }
    }

    public static void statusRow(PdfPTable t, boolean alt, String status, String... values) {
        // Renderiza igual bodyRow, mas a celula que corresponde ao valor igual
        // a {@code status} recebe background colorido. Implementacao simples:
        // o chamador posiciona o status como ultima celula e delega colorizacao.
        int statusIndex = values.length - 1;
        for (int i = 0; i < values.length; i++) {
            String v = values[i];
            PdfPCell cell = new PdfPCell(new Phrase(v == null ? "" : v, BODY_FONT));
            cell.setPadding(4F);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setBorderColor(BORDER);
            if (i == statusIndex && status != null) {
                cell.setBackgroundColor(colorForStatus(status));
            } else {
                cell.setBackgroundColor(alt ? ROW_ALT : Color.WHITE);
            }
            t.addCell(cell);
        }
    }

    public static Color colorForStatus(String status) {
        if (status == null) return Color.WHITE;
        return switch (status.toUpperCase(PT_BR)) {
            case "APROVADO" -> new Color(220, 252, 231);
            case "ALERTA" -> new Color(254, 243, 199);
            case "REPROVADO", "REJEITADO" -> new Color(254, 226, 226);
            default -> Color.WHITE;
        };
    }

    public static String formatDate(LocalDate d) {
        return d == null ? "-" : d.format(DATE_FMT);
    }

    public static String formatDecimal(Double d) {
        return d == null ? "-" : String.format(PT_BR, "%.2f", d);
    }

    public static String formatPercent(Double d) {
        return d == null ? "-" : String.format(PT_BR, "%.1f%%", d);
    }

    public static String safe(String v) {
        return (v == null || v.isBlank()) ? "-" : v.trim();
    }

    public static Paragraph section(String title) {
        Paragraph p = new Paragraph(title, SECTION_FONT);
        p.setSpacingBefore(10F);
        p.setSpacingAfter(4F);
        return p;
    }

    public static Paragraph subsection(String title) {
        Paragraph p = new Paragraph(title, SUBSECTION_FONT);
        p.setSpacingBefore(6F);
        p.setSpacingAfter(2F);
        return p;
    }

    public static PdfPCell calloutBox(String text, Color bg, Color border) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, BODY_BOLD_FONT));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(border);
        cell.setPadding(8F);
        return cell;
    }
}
