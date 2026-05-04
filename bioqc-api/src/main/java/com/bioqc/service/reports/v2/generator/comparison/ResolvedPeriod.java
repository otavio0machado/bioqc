package com.bioqc.service.reports.v2.generator.comparison;

import java.time.LocalDate;

/**
 * Periodo resolvido (com datas absolutas) para comparacoes V2.
 *
 * @param type  tipo de periodo: current-month, specific-month, year, date-range
 * @param start data inicial (inclusive)
 * @param end   data final (inclusive)
 * @param label rotulo legivel em pt-BR (ex: "Abril/2026", "Ano 2026")
 */
public record ResolvedPeriod(String type, LocalDate start, LocalDate end, String label) {
    public ResolvedPeriod {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("ResolvedPeriod.type obrigatorio");
        }
        if (start == null) {
            throw new IllegalArgumentException("ResolvedPeriod.start obrigatorio");
        }
        if (end == null) {
            throw new IllegalArgumentException("ResolvedPeriod.end obrigatorio");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end deve ser >= start");
        }
    }
}
