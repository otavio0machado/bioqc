package com.bioqc.service.reports.v2;

/**
 * Disparado quando um download/share token e solicitado para uma execucao
 * cuja retencao ja expirou. Mapeia para HTTP 410 Gone.
 */
public class ReportExpiredException extends RuntimeException {
    public ReportExpiredException(String message) {
        super(message);
    }
}
