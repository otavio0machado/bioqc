package com.bioqc.service.reports.v2;

/**
 * Disparado quando um {@code ReportCode} e solicitado mas nao existe no
 * {@code ReportDefinitionRegistry} ou nao tem generator registrado. Mapeia
 * para HTTP 404.
 */
public class ReportCodeNotFoundException extends RuntimeException {
    public ReportCodeNotFoundException(String message) {
        super(message);
    }
}
