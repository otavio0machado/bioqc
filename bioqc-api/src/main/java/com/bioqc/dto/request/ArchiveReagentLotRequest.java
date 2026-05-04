package com.bioqc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Payload do endpoint {@code POST /api/reagents/{id}/archive} (refator v3).
 *
 * <p>Validacoes adicionais (service):</p>
 * <ul>
 *   <li>{@code archivedAt &lt;= today} — rejeita data futura.</li>
 *   <li>{@code archivedBy} deve existir em {@code users} ativos com
 *       {@code role IN (ADMIN, FUNCIONARIO)}. Filtra por <strong>username</strong>
 *       (decisao audit 1.1 — username e estavel; name pode colidir).</li>
 *   <li>Lote ja {@code inativo} → 400.</li>
 * </ul>
 */
public record ArchiveReagentLotRequest(
    @NotNull LocalDate archivedAt,
    @NotBlank @Size(max = 128) String archivedBy
) {
}
