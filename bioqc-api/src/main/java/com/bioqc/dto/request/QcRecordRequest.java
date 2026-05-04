package com.bioqc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.UUID;

public record QcRecordRequest(
    @NotBlank String examName,
    @NotBlank String area,
    @NotNull LocalDate date,
    @NotBlank String level,
    String lotNumber,
    @NotNull Double value,
    @NotNull Double targetValue,
    @NotNull Double targetSd,
    @PositiveOrZero Double cvLimit,
    String equipment,
    String analyst,
    UUID referenceId
) {
}
