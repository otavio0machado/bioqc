package com.bioqc.service.reports.v2.catalog;

import java.util.List;
import java.util.Optional;

/**
 * Contrato declarativo dos filtros aceitos por uma definicao de relatorio.
 *
 * Exposto ao frontend via {@code GET /api/reports/v2/catalog} para renderizar
 * o formulario dinamicamente; usado pelo {@code FilterValidator} em runtime
 * para validar o payload antes de disparar a geracao.
 */
public record ReportFilterSpec(List<ReportFilterField> fields) {

    public ReportFilterSpec {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public Optional<ReportFilterField> field(String key) {
        if (key == null) return Optional.empty();
        return fields.stream().filter(f -> f.key().equals(key)).findFirst();
    }
}
