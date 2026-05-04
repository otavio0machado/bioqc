package com.bioqc.dto.response;

import java.time.Instant;
import java.util.UUID;

public record HematologyParameterResponse(
    UUID id,
    String analito,
    String equipamento,
    String loteControle,
    String nivelControle,
    String modo,
    Double alvoValor,
    Double minValor,
    Double maxValor,
    Double toleranciaPercentual,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {
}
