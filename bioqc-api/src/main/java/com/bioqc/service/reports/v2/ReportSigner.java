package com.bioqc.service.reports.v2;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Estampa uma pagina de assinatura eletronica em um PDF ja gerado. Gera um QR
 * code que aponta para {@code {verifyUrlBase}/r/verify/{sha256}}, adiciona
 * bloco textual com responsavel tecnico e timestamp, recalcula o SHA-256 dos
 * bytes finais e devolve tudo em um {@link SignatureResult}.
 *
 * <p>O PDF original permanece intacto — os bytes assinados sao independentes.
 *
 * <p>Implementacao: concatena uma nova pagina ao final do PDF original via
 * {@code PdfCopy}-like fluxo em OpenPDF ({@code PdfReader} + importPage).
 * Abordagem simples e robusta para A4/landscape, nao requer
 * {@code PdfStamper}.
 */
@Component
public class ReportSigner {

    private static final Font SIGN_TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(20, 83, 45));
    private static final Font SIGN_LABEL_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(70, 80, 90));
    private static final Font SIGN_VALUE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
    private static final Font SIGN_SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(110, 120, 130));
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter
        .ofPattern("dd/MM/yyyy HH:mm:ss")
        .withLocale(Locale.forLanguageTag("pt-BR"));

    public SignatureResult sign(byte[] originalBytes, SignatureRequest req) {
        if (originalBytes == null || originalBytes.length == 0) {
            throw new IllegalArgumentException("originalBytes nao pode ser vazio");
        }
        if (req == null) {
            throw new IllegalArgumentException("SignatureRequest obrigatorio");
        }
        if (req.reportSha256() == null || req.reportSha256().isBlank()) {
            throw new IllegalArgumentException("SignatureRequest.reportSha256 obrigatorio");
        }
        if (req.verifyUrlBase() == null || req.verifyUrlBase().isBlank()) {
            throw new IllegalArgumentException("SignatureRequest.verifyUrlBase obrigatorio");
        }

        Instant signedAt = Instant.now();
        String verifyUrl = trimTrailingSlash(req.verifyUrlBase()) + "/r/verify/" + req.reportSha256();

        byte[] qrPng = generateQrPng(verifyUrl, 220);
        byte[] signedPdf = appendSignaturePage(originalBytes, req, signedAt, verifyUrl, qrPng);

        String signatureHash = sha256Hex(signedPdf);
        return new SignatureResult(signedPdf, signatureHash, signedAt);
    }

    private byte[] appendSignaturePage(
        byte[] original, SignatureRequest req, Instant signedAt, String verifyUrl, byte[] qrPng
    ) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfReader reader = new PdfReader(original);
            // detecta tamanho da primeira pagina para manter continuidade visual
            com.lowagie.text.Rectangle pageSize;
            try {
                pageSize = reader.getPageSize(1);
            } catch (Exception ignored) {
                pageSize = PageSize.A4.rotate();
            }
            Document document = new Document(pageSize, 36F, 36F, 36F, 36F);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            // copia paginas originais
            com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                document.newPage();
                com.lowagie.text.pdf.PdfImportedPage page = writer.getImportedPage(reader, i);
                com.lowagie.text.Rectangle pSize = reader.getPageSize(i);
                cb.addTemplate(page, 0, 0);
                // nao e ideal para toda rotacao/escala, mas atende A4 retrato/paisagem padrao
                if (pSize.getWidth() != pageSize.getWidth() || pSize.getHeight() != pageSize.getHeight()) {
                    // se tamanho diferir, nao faz scaling — mantem conteudo posicionado no canto
                }
            }

            // pagina de assinatura
            document.newPage();
            Paragraph title = new Paragraph("Assinatura Eletronica", SIGN_TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(18F);
            document.add(title);

            PdfPTable meta = new PdfPTable(new float[] {1.2F, 2.8F});
            meta.setWidthPercentage(85F);
            meta.setHorizontalAlignment(Element.ALIGN_CENTER);
            addMetaRow(meta, "Assinado por", safeString(req.signerName()));
            addMetaRow(meta, "Registro", safeString(req.signerRegistration()));
            addMetaRow(meta, "Data/Hora", TIMESTAMP_FMT.format(signedAt.atZone(ZONE)));
            addMetaRow(meta, "Hash do documento", req.reportSha256());
            document.add(meta);

            Paragraph spacer = new Paragraph(" ", SIGN_SMALL_FONT);
            spacer.setSpacingAfter(14F);
            document.add(spacer);

            try {
                Image qr = Image.getInstance(qrPng);
                qr.setAlignment(Element.ALIGN_CENTER);
                qr.scaleAbsolute(160F, 160F);
                document.add(qr);
            } catch (Exception ignored) {
                // QR sera omitido em fallback; texto continua valido
            }

            Paragraph verify = new Paragraph(
                "Verifique a autenticidade em: " + verifyUrl, SIGN_SMALL_FONT);
            verify.setAlignment(Element.ALIGN_CENTER);
            verify.setSpacingBefore(10F);
            document.add(verify);

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao estampar pagina de assinatura", ex);
        }
    }

    private void addMetaRow(PdfPTable table, String label, String value) {
        PdfPCell lc = new PdfPCell(new Phrase(label, SIGN_LABEL_FONT));
        lc.setPadding(6F);
        lc.setBorder(com.lowagie.text.Rectangle.BOTTOM);
        lc.setBorderColor(new Color(210, 214, 218));
        table.addCell(lc);

        PdfPCell vc = new PdfPCell(new Phrase(value, SIGN_VALUE_FONT));
        vc.setPadding(6F);
        vc.setBorder(com.lowagie.text.Rectangle.BOTTOM);
        vc.setBorderColor(new Color(210, 214, 218));
        table.addCell(vc);
    }

    private byte[] generateQrPng(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                MatrixToImageWriter.writeToStream(matrix, "PNG", out);
                return out.toByteArray();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao gerar QR code", ex);
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 nao disponivel", ex);
        }
    }

    private String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String safeString(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    /**
     * Requisicao de assinatura. {@code verifyUrlBase} e tipicamente
     * {@code publicBaseUrl} das properties; {@code reportSha256} e o hash
     * dos bytes pre-assinatura.
     */
    public record SignatureRequest(
        String signerName,
        String signerRegistration,
        String verifyUrlBase,
        String reportSha256
    ) {}

    /**
     * Resultado: bytes assinados, hash SHA-256 dos bytes pos-assinatura e
     * timestamp oficial.
     */
    public record SignatureResult(
        byte[] signedBytes,
        String signatureHash,
        Instant signedAt
    ) {}
}
