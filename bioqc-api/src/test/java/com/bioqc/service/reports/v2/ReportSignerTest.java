package com.bioqc.service.reports.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportSignerTest {

    private final ReportSigner signer = new ReportSigner();

    @Test
    @DisplayName("assina PDF, recalcula hash e bytes finais diferem do original")
    void signsAndHashDiffers() throws Exception {
        byte[] original = minimalPdf("Conteudo de teste");
        ReportSigner.SignatureResult result = signer.sign(original, new ReportSigner.SignatureRequest(
            "Dr. Ana Responsavel", "CRF-12345", "http://localhost:5173", "abc123hashfake"
        ));
        assertThat(result).isNotNull();
        assertThat(result.signedBytes()).isNotNull();
        assertThat(result.signedBytes().length).isGreaterThan(original.length);
        assertThat(new String(result.signedBytes(), 0, 5)).isEqualTo("%PDF-");
        assertThat(result.signatureHash()).isNotEqualTo(sha256Hex(original));
        assertThat(result.signedAt()).isNotNull();
        // hash e hex lowercase 64 chars
        assertThat(result.signatureHash()).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("valida argumentos nulos/vazios")
    void rejectsInvalidArgs() {
        byte[] original = "fake".getBytes();
        assertThatThrownBy(() -> signer.sign(null, new ReportSigner.SignatureRequest("n", "r", "u", "h")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.sign(original, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.sign(original, new ReportSigner.SignatureRequest("n", "r", null, "h")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.sign(original, new ReportSigner.SignatureRequest("n", "r", "u", "")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private byte[] minimalPdf(String text) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph(text));
            doc.close();
            return out.toByteArray();
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = d.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
