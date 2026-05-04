package com.bioqc.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Schema do contrato HTTP de um movimento de estoque apos refator v3 (e v3.1 com
 * {@code eventDate}).
 *
 * <p>Coexistencia de campos legados e pos-V14 (decisao 1.9):</p>
 * <ul>
 *   <li>{@code previousStock} (Double) — preenchido em movimentos pre-V14, NULL pos-V14.</li>
 *   <li>{@code previousUnitsInStock} / {@code previousUnitsInUse} (Integer) — NULL em
 *       movimentos pre-V14, preenchidos pos-V14.</li>
 *   <li>{@code isLegacy} (boolean) — true quando {@code previousStock != null AND
 *       previousUnitsInStock == null}. Frontend usa para escolher qual exibir.</li>
 * </ul>
 *
 * <p>Refator v3.1: {@code eventDate} — data declarada pelo operador para o evento.
 * NULL em movimentos pre-V15 ou em registros pos-V15 sem data informada (CONSUMO,
 * ENTRADA, FECHAMENTO, AJUSTE quando o operador omite). Frontend usa
 * {@code createdAt} como fallback quando {@code eventDate == null}. Em ABERTURA
 * pos-V15, sempre preenchido (ou pelo operador, ou default = hoje).</p>
 *
 * <p>NAO ha backfill retroativo — {@code previousStock} legado nao e copiado para o par
 * novo, porque perderia a distincao auditavel entre pre-refator-v3 e pos.</p>
 */
public record StockMovementResponse(
    UUID id,
    String type,
    Double quantity,
    String responsible,
    String notes,
    Double previousStock,
    Integer previousUnitsInStock,
    Integer previousUnitsInUse,
    boolean isLegacy,
    String reason,
    LocalDate eventDate,
    Instant createdAt
) {
}
