package com.bioqc.service.reports.v2.generator.comparison;

import java.util.Optional;

/**
 * Calcula a janela de comparacao (periodo anterior) para um periodo atual.
 * Implementacoes retornam {@link Optional#empty()} para periodos que nao
 * admitem comparativo direto (ex: {@code date-range} customizado).
 */
public interface PeriodComparator {

    /**
     * Retorna a janela do periodo imediatamente anterior a {@code current}
     * ou {@code empty} se nao aplicavel.
     */
    Optional<ComparisonWindow> previousWindow(ResolvedPeriod current);
}
