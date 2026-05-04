package com.bioqc.service.reports.v2.generator.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Renderiza rodape padronizado em TODAS as paginas: linha fina +
 * "reportNumber · Pagina X de Y · Gerado em timestamp · responsibleName".
 *
 * <p>Uso: instancie com os metadados, registre em {@link PdfWriter#setPageEvent}
 * antes de abrir o documento. Cada fim de pagina chama {@link #onEndPage}.
 */
public class PdfFooterRenderer extends PdfPageEventHelper {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Font FOOTER_FONT = FontFactory.getFont(
        FontFactory.HELVETICA, 7, new Color(107, 114, 128));

    private final String reportNumber;
    private final String responsibleName;
    private final String timestamp;

    public PdfFooterRenderer(String reportNumber, String responsibleName) {
        this.reportNumber = reportNumber == null ? "-" : reportNumber;
        this.responsibleName = responsibleName == null ? "-" : responsibleName;
        this.timestamp = LocalDateTime.now(ZONE).format(FMT);
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        try {
            PdfContentByte cb = writer.getDirectContent();
            Rectangle page = document.getPageSize();
            float y = document.bottomMargin() - 12F;
            float marginLeft = document.leftMargin();
            float marginRight = page.getRight() - document.rightMargin();

            // Linha fina separadora
            cb.saveState();
            cb.setColorStroke(new Color(209, 213, 219));
            cb.setLineWidth(0.5F);
            cb.moveTo(marginLeft, y + 8F);
            cb.lineTo(marginRight, y + 8F);
            cb.stroke();
            cb.restoreState();

            // Texto do rodape
            int pageNum = writer.getPageNumber();
            String text = reportNumber + "  ·  Pagina " + pageNum
                + "  ·  Gerado em " + timestamp
                + "  ·  " + responsibleName;
            Phrase phrase = new Phrase(text, FOOTER_FONT);
            float xCenter = (marginLeft + marginRight) / 2F;
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                cb, com.lowagie.text.Element.ALIGN_CENTER, phrase, xCenter, y, 0);
        } catch (RuntimeException ex) {
            // Rodape nunca deve quebrar o documento inteiro
            // LOG omitido para minimizar dependencias
        }
    }
}
