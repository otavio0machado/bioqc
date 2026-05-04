package com.bioqc.service.reports.v2.generator.impl;

import com.bioqc.entity.LabSettings;
import com.bioqc.entity.ReportAuditLog;
import com.bioqc.service.LabSettingsService;
import com.bioqc.service.ReportNumberingService;
import com.bioqc.service.reports.v2.generator.GenerationContext;
import com.bioqc.service.reports.v2.generator.ai.ReportAiCommentator;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/**
 * Stubs compartilhados pelos smoke tests dos generators. Evita duplicacao
 * de anonymous subclasses em cada teste.
 */
final class GeneratorTestSupport {

    private GeneratorTestSupport() {}

    static final String STABLE_REPORT_NUMBER = "BIO-202604-000042";

    /** Stub de {@link ReportNumberingService} com numero estavel + sha256 real. */
    static ReportNumberingService stubNumbering() {
        return new ReportNumberingService(null, null) {
            @Override
            public String reserveNextNumber() {
                return STABLE_REPORT_NUMBER;
            }

            @Override
            public ReportAuditLog registerGeneration(
                String reportNumber, String area, String format, String periodLabel, byte[] content, UUID generatedBy
            ) {
                return null;
            }

            @Override
            public String sha256Hex(byte[] content) {
                try {
                    java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
                    byte[] h = d.digest(content);
                    StringBuilder sb = new StringBuilder();
                    for (byte b : h) sb.append(String.format("%02x", b));
                    return sb.toString();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
    }

    /** Stub de LabSettingsService retornando singleton minimo. */
    static LabSettingsService stubLabSettings() {
        return new LabSettingsService(null, null) {
            @Override
            public LabSettings getOrCreateSingleton() {
                return LabSettings.builder().labName("Lab Teste").responsibleName("Dr. Teste").build();
            }
        };
    }

    /** IA stub — retorna string fixa, util para checar presenca no PDF. */
    static ReportAiCommentator stubAi(String fixedText) {
        return (code, ctx, gen) -> fixedText;
    }

    /** Contexto de geracao minimo para testes. */
    static GenerationContext ctx() {
        return new GenerationContext(
            UUID.randomUUID(),
            "tester",
            Set.of("ADMIN"),
            Instant.now(),
            ZoneId.of("America/Sao_Paulo"),
            LabSettings.builder().labName("Lab Teste").responsibleName("Dr. Teste").build(),
            "corr-test",
            "req-test"
        );
    }

    /**
     * Extrai texto concatenado de todas paginas de um PDF. Usado nos
     * asserts dos smoke tests para verificar presenca de strings-chave.
     * OpenPDF inclui {@code PdfTextExtractor} em com.lowagie.text.pdf.parser.
     */
    static String extractPdfText(byte[] bytes) {
        try {
            PdfReader reader = new PdfReader(bytes);
            StringBuilder sb = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                sb.append(extractor.getTextFromPage(i)).append('\n');
            }
            reader.close();
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Falha ao extrair texto do PDF", ex);
        }
    }

    static void assertPdfMagicHeader(byte[] bytes) {
        if (bytes == null || bytes.length < 5) {
            throw new AssertionError("PDF vazio");
        }
        String header = new String(bytes, 0, 5);
        if (!header.equals("%PDF-")) {
            throw new AssertionError("Bytes nao comecam com %PDF-: " + header);
        }
    }
}
