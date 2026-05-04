package com.bioqc.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Payload de cadastro/edicao de lote de reagente apos refator v3.
 *
 * <p>Campos obrigatorios canonicos: {@code label}, {@code lotNumber}, {@code manufacturer},
 * {@code category}, {@code unitsInStock}, {@code unitsInUse}, {@code status},
 * {@code expiryDate}, {@code location}, {@code storageTemp}.</p>
 *
 * <p>{@code label} substitui o antigo {@code name} — coluna {@code reagent_lots.name}
 * permanece como agrupador de etiqueta no banco.</p>
 *
 * <p>Mudancas v3:</p>
 * <ul>
 *   <li>DROP {@code currentStock: Double}.</li>
 *   <li>ADD {@code unitsInStock: Integer} (NotNull, Min 0).</li>
 *   <li>ADD {@code unitsInUse: Integer} (NotNull, Min 0).</li>
 *   <li>{@code status} REJEITA {@code 'inativo'} (use {@code POST /archive}).</li>
 * </ul>
 *
 * <p>{@code category} e {@code storageTemp} aceitam apenas valores das listas fechadas
 * em {@code constants.ts}; o servico valida via lista explicita.</p>
 */
public record ReagentLotRequest(
    @NotBlank @Size(max = 255) String label,
    @NotBlank @Size(max = 255) String lotNumber,
    @NotBlank @Size(max = 128) String manufacturer,
    @NotBlank String category,
    @NotNull @Min(0) Integer unitsInStock,
    @NotNull @Min(0) Integer unitsInUse,
    @NotBlank String status,
    @NotNull LocalDate expiryDate,
    @NotBlank @Size(max = 128) String location,
    @NotBlank String storageTemp,
    @Size(max = 128) String supplier,
    LocalDate receivedDate,
    LocalDate openedDate
) {
}
