package com.bioqc.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Schema do contrato HTTP do lote de reagente apos refator v3.
 *
 * <p>{@code label} substitui o antigo {@code name}. Saem do response (refator v2):
 * {@code quantityValue}, {@code stockUnit}, {@code estimatedConsumption}, {@code startDate},
 * {@code endDate}, {@code alertThresholdDays}, {@code stockPct}, {@code daysToRupture}.</p>
 *
 * <p>Mudancas v3:</p>
 * <ul>
 *   <li>DROP {@code currentStock: Double}.</li>
 *   <li>ADD {@code unitsInStock}, {@code unitsInUse}, {@code totalUnits} (derivado).</li>
 *   <li>ADD {@code archivedAt}, {@code archivedBy}, {@code needsStockReview}.</li>
 * </ul>
 */
public record ReagentLotResponse(
    UUID id,
    String label,
    String lotNumber,
    String manufacturer,
    String category,
    LocalDate expiryDate,
    Integer unitsInStock,
    Integer unitsInUse,
    Integer totalUnits,
    String storageTemp,
    String status,
    Instant createdAt,
    Instant updatedAt,
    long daysLeft,
    boolean nearExpiry,
    String location,
    String supplier,
    LocalDate receivedDate,
    LocalDate openedDate,
    LocalDate archivedAt,
    String archivedBy,
    boolean needsStockReview,
    /**
     * Flag derivada: true quando o lote (match por lotNumber) apareceu em pelo menos
     * um registro de CQ nos ultimos 30 dias. Permite que o frontend destaque lotes
     * ativos em CQ e bloqueia decisoes de descarte apressadas.
     */
    boolean usedInQcRecently,
    /**
     * Diagnostico operacional derivado pelo backend. Alimenta a fila de saneamento
     * cadastral. Campos chave (ASCII): manufacturer, location, supplier, receivedDate.
     */
    boolean traceabilityComplete,
    List<String> traceabilityIssues,
    /**
     * Politica de movimentacao derivada do estado do lote. Lote {@code vencido} e
     * {@code inativo} nao aceitam {@code ENTRADA}. {@code inativo} permite apenas {@code AJUSTE}.
     */
    boolean canReceiveEntry,
    List<String> allowedMovementTypes,
    String movementWarning
) {
}
