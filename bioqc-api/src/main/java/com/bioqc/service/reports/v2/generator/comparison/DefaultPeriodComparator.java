package com.bioqc.service.reports.v2.generator.comparison;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Implementacao padrao de {@link PeriodComparator}. Regras:
 * <ul>
 *   <li>{@code current-month}: mes anterior completo</li>
 *   <li>{@code specific-month}: mes anterior calendario (jan -> dez do ano anterior)</li>
 *   <li>{@code year}: ano anterior inteiro</li>
 *   <li>{@code date-range}: {@code Optional.empty()}</li>
 * </ul>
 */
@Component
public class DefaultPeriodComparator implements PeriodComparator {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    @Override
    public Optional<ComparisonWindow> previousWindow(ResolvedPeriod current) {
        if (current == null) return Optional.empty();
        String type = current.type();
        return switch (type) {
            case "current-month" -> {
                YearMonth currentMonth = YearMonth.from(current.start());
                YearMonth previous = currentMonth.minusMonths(1);
                yield Optional.of(new ComparisonWindow(
                    previous.atDay(1),
                    previous.atEndOfMonth(),
                    labelForMonth(previous)
                ));
            }
            case "specific-month" -> {
                YearMonth currentMonth = YearMonth.from(current.start());
                YearMonth previous = currentMonth.minusMonths(1);
                yield Optional.of(new ComparisonWindow(
                    previous.atDay(1),
                    previous.atEndOfMonth(),
                    labelForMonth(previous)
                ));
            }
            case "year" -> {
                int year = current.start().getYear() - 1;
                yield Optional.of(new ComparisonWindow(
                    LocalDate.of(year, 1, 1),
                    LocalDate.of(year, 12, 31),
                    "Ano " + year
                ));
            }
            default -> Optional.empty();
        };
    }

    private String labelForMonth(YearMonth ym) {
        String name = ym.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        String cap = name.substring(0, 1).toUpperCase(PT_BR) + name.substring(1);
        return cap + "/" + ym.getYear();
    }
}
