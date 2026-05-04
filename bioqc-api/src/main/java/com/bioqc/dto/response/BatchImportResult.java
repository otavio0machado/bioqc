package com.bioqc.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Resultado detalhado de uma importacao em lote.
 *
 * Sempre retornado pelo endpoint {@code /api/qc-records/batch?mode=partial};
 * o modo ATOMIC usa esta estrutura quando ao menos um registro falha.
 */
public record BatchImportResult(
    UUID runId,
    String mode,          // ATOMIC ou PARTIAL
    int total,
    int successCount,
    int failureCount,
    List<RowResult> results
) {

    /** Resultado de uma linha da planilha. */
    public record RowResult(
        int rowIndex,       // 0-based na lista enviada
        boolean success,
        String message,     // erro legivel
        QcRecordResponse record  // populado quando success=true
    ) {
    }
}
