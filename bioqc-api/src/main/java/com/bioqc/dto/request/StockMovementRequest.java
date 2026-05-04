package com.bioqc.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/**
 * Payload de movimento de estoque apos refator v3 (e v3.1 com {@code eventDate}).
 *
 * <p>Tipos aceitos em escrita: {@code ENTRADA, ABERTURA, FECHAMENTO, CONSUMO, AJUSTE}.
 * {@code SAIDA} e descontinuado em escrita (read-only para historico).</p>
 *
 * <p>Validacoes cross-field aplicadas pelo service:</p>
 * <ul>
 *   <li>AJUSTE exige {@code targetUnitsInStock} e {@code targetUnitsInUse} (Min 0)
 *       e {@code reason} obrigatorio.</li>
 *   <li>ABERTURA / FECHAMENTO exigem {@code quantity == 1} (rejeita 400 se diferente).</li>
 *   <li>FECHAMENTO sem {@code reason} usa default {@code REVERSAO_ABERTURA}.</li>
 *   <li>CONSUMO em lote {@code vencido} exige {@code reason} (descarte registrado).</li>
 *   <li>ENTRADA / CONSUMO exigem {@code quantity > 0}.</li>
 *   <li>v3.1: {@code eventDate} (data declarada do evento) e opcional. Quando
 *       preenchido, deve ser {@code <= hoje} (sem evento futuro). Em ABERTURA,
 *       se ausente, default = hoje (compatibilidade com v3); se presente,
 *       sincroniza {@code lot.openedDate} na primeira abertura. Em CONSUMO/Final
 *       de Uso, rastreabilidade ANVISA RDC 302 art. 49. Em
 *       ENTRADA/FECHAMENTO/AJUSTE, apenas armazena (sem efeito colateral).</li>
 * </ul>
 */
public record StockMovementRequest(
    @NotBlank String type,
    @NotNull @PositiveOrZero Double quantity,
    @NotBlank String responsible,
    String notes,
    String reason,
    @Min(0) Integer targetUnitsInStock,
    @Min(0) Integer targetUnitsInUse,
    LocalDate eventDate
) {
}
