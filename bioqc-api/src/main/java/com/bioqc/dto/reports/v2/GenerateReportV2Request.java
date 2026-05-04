package com.bioqc.dto.reports.v2;

import com.bioqc.service.reports.v2.catalog.ReportCode;
import com.bioqc.service.reports.v2.catalog.ReportFormat;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Payload de {@code POST /api/reports/v2/generate}.
 *
 * @param code    codigo do relatorio (obrigatorio)
 * @param format  formato desejado; default PDF se null
 * @param filters mapa cru de filtros (validado contra a {@link com.bioqc.service.reports.v2.catalog.ReportFilterSpec} da definicao)
 */
public record GenerateReportV2Request(
    @NotNull ReportCode code,
    ReportFormat format,
    Map<String, Object> filters
) {}
