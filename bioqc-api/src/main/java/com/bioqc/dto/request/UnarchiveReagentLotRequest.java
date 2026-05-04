package com.bioqc.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Payload do endpoint {@code POST /api/reagents/{id}/unarchive} (refator v3).
 *
 * <p>{@code reason} e opcional — quando presente, e gravado em
 * {@code audit_log.details} para reconstrucao auditavel da reativacao.</p>
 */
public record UnarchiveReagentLotRequest(
    @Size(max = 256) String reason
) {
}
