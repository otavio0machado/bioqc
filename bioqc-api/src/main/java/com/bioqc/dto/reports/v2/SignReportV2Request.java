package com.bioqc.dto.reports.v2;

/**
 * Payload opcional para {@code POST /api/reports/v2/executions/{id}/sign}.
 * Quando os campos chegam vazios/null, o service usa os dados do
 * {@code LabSettings} (responsavel tecnico configurado).
 */
public record SignReportV2Request(
    String signerName,
    String signerRegistration
) {}
