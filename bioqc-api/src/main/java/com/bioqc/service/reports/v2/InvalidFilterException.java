package com.bioqc.service.reports.v2;

import java.util.Collections;
import java.util.List;

/**
 * Disparado pelo {@code FilterValidator} quando o payload de filtros viola a
 * {@code ReportFilterSpec}. Mapeia para HTTP 422.
 */
public class InvalidFilterException extends RuntimeException {

    private final List<String> violations;

    public InvalidFilterException(String message) {
        super(message);
        this.violations = List.of(message);
    }

    public InvalidFilterException(List<String> violations) {
        super(String.join("; ", violations == null ? List.of() : violations));
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public List<String> getViolations() {
        return Collections.unmodifiableList(violations);
    }
}
