package com.bioqc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload do endpoint {@code DELETE /api/reagents/{id}} (refator v3).
 *
 * <p>Confirmacao explicita por digitacao do {@code lotNumber} — defesa contra
 * delete acidental. Service compara
 * {@code request.confirmLotNumber.trim() == lot.lotNumber.trim()} (case-sensitive,
 * matching exato — ANVISA-grade). Mismatch → 400.</p>
 *
 * <p>Permissao do endpoint: {@code @PreAuthorize("hasRole('ADMIN')")}.
 * FUNCIONARIO recebe 403.</p>
 */
public record DeleteReagentLotRequest(
    @NotBlank @Size(max = 255) String confirmLotNumber
) {
}
