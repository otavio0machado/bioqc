package com.bioqc.dto.reports.v2;

import java.util.List;
import java.util.Set;

/**
 * Representacao do {@code ReportDefinition} exposta ao frontend pelo
 * endpoint {@code GET /api/reports/v2/catalog}. Usa tipos de String para
 * enums e subestruturas porque o frontend trabalha com string unions.
 */
public record ReportDefinitionResponse(
    String code,
    String name,
    String description,
    String subtitle,
    String icon,
    String category,
    Set<String> supportedFormats,
    List<FilterFieldDto> filters,
    Set<String> roleAccess,
    boolean signatureRequired,
    boolean previewSupported,
    boolean aiCommentaryCapable,
    int retentionDays,
    String legalBasis
) {

    public record FilterFieldDto(
        String key,
        String type,
        boolean required,
        List<String> allowedValues,
        String label,
        String helpText
    ) {}
}
