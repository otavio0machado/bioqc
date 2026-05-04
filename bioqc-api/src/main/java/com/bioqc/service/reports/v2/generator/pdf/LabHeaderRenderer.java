package com.bioqc.service.reports.v2.generator.pdf;

import com.bioqc.entity.LabSettings;
import com.bioqc.service.reports.v2.catalog.ReportDefinition;
import com.bioqc.service.reports.v2.generator.ReportArtifact;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Renderiza o cabecalho institucional de um PDF V2. Inclui logo, dados
 * completos do laboratorio (CNPJ, CNES, endereco), responsavel tecnico,
 * bloco do laudo (numero, data, periodo), titulo e hash SHA-256.
 *
 * <p>Degrade: se {@code cnpj} esta nulo/vazio, exibe "CNPJ: nao cadastrado"
 * em vermelho (warning visual) sem quebrar a renderizacao. Se o logo falha,
 * mostra apenas o texto.
 */
@Component
public class LabHeaderRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(LabHeaderRenderer.class);
    private static final String LOGO_PATH = "reports/logos/logo_bio.png";
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Color BRAND_DARK = new Color(20, 83, 45);
    private static final Color BRAND_PRIMARY = new Color(22, 101, 52);
    private static final Color MUTED = new Color(75, 85, 99);
    private static final Color WARNING = new Color(220, 38, 38);

    private static final Font LAB_NAME_FONT = FontFactory.getFont(FontFactory.TIMES_BOLD, 16, BRAND_DARK);
    private static final Font LAB_DATA_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED);
    private static final Font LAB_DATA_WARN_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, WARNING);
    private static final Font RESP_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRAND_PRIMARY);
    private static final Font LAUDO_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED);
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND_DARK);
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, MUTED);
    private static final Font HASH_FONT = FontFactory.getFont(FontFactory.COURIER, 7, new Color(107, 114, 128));

    /**
     * Renderiza o cabecalho no topo do documento.
     *
     * @param doc      documento aberto
     * @param writer   writer (nao usado diretamente, reservado para evolucoes)
     * @param settings configuracoes do laboratorio (pode ter campos nulos)
     * @param def      definicao do relatorio (para titulo/subtitulo)
     * @param artifact artefato (fornece reportNumber, periodLabel, sha256). Pode
     *                 ser {@code null} em geracoes em que o numero ainda nao foi reservado
     */
    public void render(Document doc, PdfWriter writer, LabSettings settings,
                       ReportDefinition def, ReportArtifact artifact) {
        try {
            PdfPTable header = new PdfPTable(new float[] { 1.2F, 3.8F });
            header.setWidthPercentage(100F);
            header.setSpacingAfter(6F);

            // Celula 1: logo
            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
            logoCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Image logo = tryLoadLogo();
            if (logo != null) {
                logo.scaleToFit(120F, 60F);
                logoCell.addElement(logo);
            } else {
                logoCell.addElement(new Paragraph(" ", LAB_NAME_FONT));
            }
            header.addCell(logoCell);

            // Celula 2: nome + dados do lab
            PdfPCell labCell = new PdfPCell();
            labCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
            labCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            String labName = settings != null && notBlank(settings.getLabName())
                ? settings.getLabName() : "Laboratorio BioQC";
            Paragraph nameP = new Paragraph(labName, LAB_NAME_FONT);
            nameP.setAlignment(Element.ALIGN_RIGHT);
            labCell.addElement(nameP);

            String cnpj = extractField(settings, "cnpj");
            String cnes = extractField(settings, "cnes");
            String address = settings != null ? settings.getAddress() : "";
            Paragraph labLine = new Paragraph();
            labLine.setAlignment(Element.ALIGN_RIGHT);

            boolean hasCnpj = notBlank(cnpj);
            if (hasCnpj) {
                labLine.add(new Phrase("CNPJ: " + cnpj, LAB_DATA_FONT));
            } else {
                labLine.add(new Phrase("CNPJ: nao cadastrado", LAB_DATA_WARN_FONT));
            }
            if (notBlank(cnes)) {
                labLine.add(new Phrase(" | CNES: " + cnes, LAB_DATA_FONT));
            }
            if (notBlank(address)) {
                labLine.add(new Phrase(" | " + address, LAB_DATA_FONT));
            }
            labCell.addElement(labLine);

            // Responsavel tecnico
            String respName = settings != null ? settings.getResponsibleName() : "";
            String respReg = settings != null ? settings.getResponsibleRegistration() : "";
            String regBody = extractField(settings, "registrationBody");
            if (notBlank(respName) || notBlank(respReg)) {
                StringBuilder sb = new StringBuilder("Responsavel Tecnico: ");
                sb.append(notBlank(respName) ? respName : "-");
                if (notBlank(respReg)) {
                    sb.append(", ").append(notBlank(regBody) ? regBody : "Reg.")
                      .append(" ").append(respReg);
                }
                Paragraph respP = new Paragraph(sb.toString(), RESP_FONT);
                respP.setAlignment(Element.ALIGN_RIGHT);
                labCell.addElement(respP);
            }
            header.addCell(labCell);

            doc.add(header);

            // Bloco do laudo
            if (artifact != null) {
                String laudoLine = "Laudo Numero: " + (artifact.reportNumber() == null ? "-" : artifact.reportNumber())
                    + "  |  Emitido em: " + LocalDateTime.now(ZONE).format(DATETIME_FMT)
                    + "  |  Periodo: " + (artifact.periodLabel() == null ? "-" : artifact.periodLabel());
                Paragraph laudoP = new Paragraph(laudoLine, LAUDO_FONT);
                laudoP.setAlignment(Element.ALIGN_CENTER);
                laudoP.setSpacingAfter(8F);
                doc.add(laudoP);
            }

            // Titulo
            if (def != null) {
                Paragraph title = new Paragraph(def.name(), TITLE_FONT);
                title.setAlignment(Element.ALIGN_CENTER);
                title.setSpacingAfter(2F);
                doc.add(title);
                if (notBlank(def.description())) {
                    Paragraph sub = new Paragraph(def.description(), SUBTITLE_FONT);
                    sub.setAlignment(Element.ALIGN_CENTER);
                    sub.setSpacingAfter(6F);
                    doc.add(sub);
                }
            }

            // Hash — suprimido quando for o placeholder sentinel passado durante a
            // renderizacao (generator ainda nao fechou o PDF, logo o hash real nao
            // existe). Dominio audit apontou que expor "Hash: 0000..." no laudo
            // regulatorio e pior que omitir. Hash real vai no `ReportAuditLog`.
            if (artifact != null && notBlank(artifact.sha256()) && !isSentinelHash(artifact.sha256())) {
                String shortHash = artifact.sha256().length() > 16
                    ? artifact.sha256().substring(0, 16) + "..."
                    : artifact.sha256();
                Paragraph hashP = new Paragraph("Hash SHA-256: " + shortHash, HASH_FONT);
                hashP.setAlignment(Element.ALIGN_CENTER);
                hashP.setSpacingAfter(10F);
                doc.add(hashP);
            }

        } catch (DocumentException ex) {
            LOG.warn("Falha ao renderizar cabecalho institucional", ex);
        }
    }

    private Image tryLoadLogo() {
        try (InputStream is = new ClassPathResource(LOGO_PATH).getInputStream()) {
            byte[] bytes = is.readAllBytes();
            return Image.getInstance(bytes);
        } catch (IOException ex) {
            LOG.debug("Logo {} nao encontrado; seguindo sem logo", LOGO_PATH);
            return null;
        } catch (Exception ex) {
            LOG.warn("Falha ao carregar logo {}; seguindo sem logo", LOGO_PATH, ex);
            return null;
        }
    }

    private String extractField(LabSettings settings, String fieldName) {
        if (settings == null) return null;
        // Usa reflection defensiva: campos novos (cnpj, cnes, etc.) podem ou nao
        // existir conforme a evolucao V10. Se nao existir, retorna null.
        try {
            java.lang.reflect.Method getter = settings.getClass().getMethod(
                "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1));
            Object value = getter.invoke(settings);
            return value == null ? null : value.toString();
        } catch (NoSuchMethodException ex) {
            return null;
        } catch (ReflectiveOperationException ex) {
            LOG.debug("Falha ao extrair campo {} de LabSettings", fieldName);
            return null;
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Generators nao conseguem calcular o hash real antes de {@code document.close()}. */
    private boolean isSentinelHash(String hash) {
        if (hash == null) return false;
        for (int i = 0; i < hash.length(); i++) {
            if (hash.charAt(i) != '0') return false;
        }
        return hash.length() > 0;
    }
}
