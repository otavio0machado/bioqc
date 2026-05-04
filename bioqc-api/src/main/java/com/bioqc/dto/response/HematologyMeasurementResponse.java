package com.bioqc.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HematologyMeasurementResponse(
    UUID id,
    UUID parameterId,
    String parameterEquipamento,
    String parameterLoteControle,
    String parameterNivelControle,
    LocalDate dataMedicao,
    String analito,
    Double valorMedido,
    String modoUsado,
    Double minAplicado,
    Double maxAplicado,
    String status,
    String observacao,
    Instant createdAt
) {
}
