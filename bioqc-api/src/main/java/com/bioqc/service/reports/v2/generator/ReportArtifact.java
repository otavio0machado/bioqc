package com.bioqc.service.reports.v2.generator;

import java.util.List;

/**
 * Resultado imutavel de uma geracao bem-sucedida. Agrega os bytes do arquivo,
 * metadados de entrega e o hash SHA-256 dos bytes pre-assinatura.
 *
 * @param bytes             conteudo binario do relatorio (PDF/XLSX/HTML)
 * @param contentType       MIME type (ex.: application/pdf)
 * @param suggestedFilename nome sugerido para download
 * @param pageCount         numero de paginas (PDF); 0 para formatos sem paginacao
 * @param sizeBytes         tamanho em bytes de {@link #bytes()}
 * @param reportNumber      numero oficial BIO-AAAAMM-NNNNNN
 * @param sha256            hash hex lowercase dos bytes entregues (antes de assinatura)
 * @param periodLabel       rotulo do periodo para exibicao
 * @param warnings          avisos estruturados sobre geracao parcial (ex.: secoes
 *                          do pacote regulatorio que falharam). Lista vazia quando
 *                          tudo gerou sem incidentes. NAO substitui excecoes —
 *                          falhas totais ainda lancam; warnings documentam
 *                          sucessos degradados.
 */
public record ReportArtifact(
    byte[] bytes,
    String contentType,
    String suggestedFilename,
    int pageCount,
    long sizeBytes,
    String reportNumber,
    String sha256,
    String periodLabel,
    List<String> warnings
) {
    public ReportArtifact {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("ReportArtifact.bytes nao pode ser vazio");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("ReportArtifact.contentType obrigatorio");
        }
        if (suggestedFilename == null || suggestedFilename.isBlank()) {
            throw new IllegalArgumentException("ReportArtifact.suggestedFilename obrigatorio");
        }
        if (reportNumber == null || reportNumber.isBlank()) {
            throw new IllegalArgumentException("ReportArtifact.reportNumber obrigatorio");
        }
        if (sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("ReportArtifact.sha256 obrigatorio");
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * Ctor-compat para call-sites pre-existentes que nao passam warnings.
     * Preserva binario e contratos antigos; warnings fica vazio.
     */
    public ReportArtifact(
        byte[] bytes,
        String contentType,
        String suggestedFilename,
        int pageCount,
        long sizeBytes,
        String reportNumber,
        String sha256,
        String periodLabel
    ) {
        this(bytes, contentType, suggestedFilename, pageCount, sizeBytes, reportNumber, sha256, periodLabel, List.of());
    }
}
