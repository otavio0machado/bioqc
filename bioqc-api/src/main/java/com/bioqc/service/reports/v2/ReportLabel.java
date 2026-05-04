package com.bioqc.service.reports.v2;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Rotulos pre-definidos que podem ser aplicados a uma execucao de relatorio
 * V2 via endpoint {@code POST /api/reports/v2/executions/{id}/labels}.
 *
 * <p>Persistido em {@code report_runs.labels} como CSV. Valores sao
 * lowercase snake_case.
 */
public enum ReportLabel {
    OFICIAL_MENSAL("oficial_mensal"),
    ENTREGUE_VIGILANCIA("entregue_vigilancia"),
    RASCUNHO("rascunho"),
    REVISAO_INTERNA("revisao_interna");

    private final String value;

    ReportLabel(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<ReportLabel> parse(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return Optional.empty();
        return Arrays.stream(values())
            .filter(l -> l.value.equals(trimmed))
            .findFirst();
    }

    public static List<String> allValues() {
        return Arrays.stream(values()).map(ReportLabel::value).collect(Collectors.toUnmodifiableList());
    }
}
