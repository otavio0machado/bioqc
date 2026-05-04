package com.bioqc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AreaQcParameterRequest(
    @NotBlank String analito,
    String equipamento,
    String loteControle,
    String nivelControle,
    @NotBlank String modo,
    @NotNull Double alvoValor,
    Double minValor,
    Double maxValor,
    Double toleranciaPercentual
) {
}
