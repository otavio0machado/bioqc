package com.bioqc.dto.reports.v2;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resposta canonica de uma execucao V2, usada por {@code /generate},
 * {@code /executions/{id}}, {@code /executions/*} e {@code /sign}.
 *
 * <p>Campos relacionados a assinatura:
 * <ul>
 *   <li>{@code sha256} - hash do PDF original (sempre presente em SUCCESS)</li>
 *   <li>{@code signatureHash} - hash da versao assinada (null ate /sign)</li>
 *   <li>{@code signedSha256} - alias de {@code signatureHash}, mantido para
 *     clientes novos que queiram discriminar contra {@code sha256} sem ambiguidade.
 *     O conteudo e identico ao de {@code signatureHash}; ambos sao expostos por
 *     clareza de contrato.</li>
 * </ul>
 *
 * <p>Campo {@code labels} lista rotulos associados (ex: "oficial_mensal",
 * "entregue_vigilancia"). Sempre nao-nula; vazia quando nao aplicados.
 */
public record ReportExecutionResponse(
    UUID id,
    String reportCode,
    String format,
    String status,
    String reportNumber,
    String sha256,
    String signatureHash,
    String signedSha256,
    Long sizeBytes,
    Integer pageCount,
    String username,
    Instant createdAt,
    Instant signedAt,
    Instant expiresAt,
    String downloadUrl,
    String verifyUrl,
    String periodLabel,
    List<String> labels,
    List<String> warnings
) {
    public ReportExecutionResponse {
        if (labels == null) labels = List.of();
        if (warnings == null) warnings = List.of();
    }

    /**
     * Ctor-compat: clientes/mappers pre-existentes que ainda nao passam
     * warnings. Mantido para nao quebrar chamadas de V1/testes historicos.
     */
    public ReportExecutionResponse(
        UUID id,
        String reportCode,
        String format,
        String status,
        String reportNumber,
        String sha256,
        String signatureHash,
        String signedSha256,
        Long sizeBytes,
        Integer pageCount,
        String username,
        Instant createdAt,
        Instant signedAt,
        Instant expiresAt,
        String downloadUrl,
        String verifyUrl,
        String periodLabel,
        List<String> labels
    ) {
        this(id, reportCode, format, status, reportNumber, sha256, signatureHash,
            signedSha256, sizeBytes, pageCount, username, createdAt, signedAt,
            expiresAt, downloadUrl, verifyUrl, periodLabel, labels, List.of());
    }
}
