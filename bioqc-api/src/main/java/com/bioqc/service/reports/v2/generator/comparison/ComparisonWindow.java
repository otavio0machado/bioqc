package com.bioqc.service.reports.v2.generator.comparison;

import java.time.LocalDate;

/**
 * Janela temporal para comparacao com o periodo atual (ex: mes anterior).
 *
 * @param start data inicial (inclusive)
 * @param end   data final (inclusive)
 * @param label rotulo legivel em pt-BR
 */
public record ComparisonWindow(LocalDate start, LocalDate end, String label) {
    public ComparisonWindow {
        if (start == null) throw new IllegalArgumentException("ComparisonWindow.start obrigatorio");
        if (end == null) throw new IllegalArgumentException("ComparisonWindow.end obrigatorio");
        if (end.isBefore(start)) throw new IllegalArgumentException("end deve ser >= start");
        if (label == null) label = "";
    }
}
