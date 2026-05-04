package com.bioqc.dto.reports.v2;

import java.util.List;

/**
 * Resposta do {@code POST /api/reports/v2/preview}.
 */
public record PreviewResponse(String html, List<String> warnings, String periodLabel) {}
