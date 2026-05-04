package com.bioqc.dto.request;

import jakarta.validation.constraints.NotBlank;

public record HematologyParameterRequest(
    @NotBlank String analito,
    String equipamento,
    String loteControle,
    String nivelControle,
    @NotBlank String modo,
    Double alvoValor,
    Double minValor,
    Double maxValor,
    Double toleranciaPercentual
) {
}
