package com.bioqc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record HematologyMeasurementRequest(
    @NotNull UUID parameterId,
    @NotNull LocalDate dataMedicao,
    @NotBlank String analito,
    @NotNull Double valorMedido,
    String observacao
) {
}
