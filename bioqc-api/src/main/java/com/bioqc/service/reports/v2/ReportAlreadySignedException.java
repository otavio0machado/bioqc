package com.bioqc.service.reports.v2;

/**
 * Disparado quando /sign e chamado em uma execucao que ja foi assinada.
 * Mapeia para HTTP 409.
 */
public class ReportAlreadySignedException extends RuntimeException {
    public ReportAlreadySignedException(String message) {
        super(message);
    }
}
