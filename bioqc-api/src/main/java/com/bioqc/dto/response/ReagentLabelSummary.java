package com.bioqc.dto.response;

/**
 * Resumo agregado de lotes por etiqueta ({@code label}).
 *
 * <p>Refator v3: campo {@code foraDeEstoque} renomeado para {@code inativos} no contrato
 * (espelha drop de status {@code fora_de_estoque} e add de {@code inativo}).</p>
 */
public record ReagentLabelSummary(
    String label,
    long total,
    long emEstoque,
    long emUso,
    long inativos,
    long vencidos
) {
}
