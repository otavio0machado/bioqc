package com.bioqc.service.reports.v2.generator.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultPeriodComparatorTest {

    private final DefaultPeriodComparator comparator = new DefaultPeriodComparator();

    @Test
    @DisplayName("current-month retorna mes anterior completo")
    void currentMonthReturnsPreviousMonth() {
        ResolvedPeriod current = new ResolvedPeriod(
            "current-month", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), "Abril/2026");
        Optional<ComparisonWindow> prev = comparator.previousWindow(current);
        assertThat(prev).isPresent();
        assertThat(prev.get().start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(prev.get().end()).isEqualTo(LocalDate.of(2026, 3, 31));
        // Nome do mes em pt-BR ("marco" ou "março" conforme ICU)
        assertThat(prev.get().label()).endsWith("/2026").containsIgnoringCase("ma");
    }

    @Test
    @DisplayName("specific-month retorna mes anterior calendario")
    void specificMonthReturnsPreviousCalendarMonth() {
        ResolvedPeriod current = new ResolvedPeriod(
            "specific-month", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "Maio/2026");
        Optional<ComparisonWindow> prev = comparator.previousWindow(current);
        assertThat(prev).isPresent();
        assertThat(prev.get().start()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(prev.get().end()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("specific-month em janeiro retorna dezembro do ano anterior")
    void specificMonthJanuaryReturnsPreviousYearDecember() {
        ResolvedPeriod current = new ResolvedPeriod(
            "specific-month", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "Janeiro/2026");
        Optional<ComparisonWindow> prev = comparator.previousWindow(current);
        assertThat(prev).isPresent();
        assertThat(prev.get().start()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(prev.get().end()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(prev.get().label()).isEqualTo("Dezembro/2025");
    }

    @Test
    @DisplayName("year retorna ano anterior inteiro")
    void yearReturnsPreviousYear() {
        ResolvedPeriod current = new ResolvedPeriod(
            "year", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "Ano 2026");
        Optional<ComparisonWindow> prev = comparator.previousWindow(current);
        assertThat(prev).isPresent();
        assertThat(prev.get().start()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(prev.get().end()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(prev.get().label()).isEqualTo("Ano 2025");
    }

    @Test
    @DisplayName("date-range retorna Optional.empty()")
    void dateRangeReturnsEmpty() {
        ResolvedPeriod current = new ResolvedPeriod(
            "date-range", LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 20), "10/04 a 20/04");
        Optional<ComparisonWindow> prev = comparator.previousWindow(current);
        assertThat(prev).isEmpty();
    }

    @Test
    @DisplayName("null retorna Optional.empty()")
    void nullReturnsEmpty() {
        assertThat(comparator.previousWindow(null)).isEmpty();
    }
}
