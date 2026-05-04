package com.bioqc.service.reports.v2;

/**
 * Disparado por {@code ReportServiceV2.sign(...)} quando o responsavel tecnico
 * resolvido (payload -> LabSettings) nao possui registro profissional (CRBM/CRM)
 * valido. Laudos sem registro nao tem valor juridico no Brasil.
 * Mapeia para HTTP 422.
 */
public class InvalidSignerException extends RuntimeException {

    public InvalidSignerException(String message) {
        super(message);
    }
}
