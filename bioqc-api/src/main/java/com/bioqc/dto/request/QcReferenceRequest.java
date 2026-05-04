package com.bioqc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record QcReferenceRequest(
    @NotNull UUID examId,
    @NotBlank String name,
    @NotBlank String level,
    String lotNumber,
    String manufacturer,
    @NotNull Double targetValue,
    @NotNull Double targetSd,
    @NotNull Double cvMaxThreshold,
    LocalDate validFrom,
    LocalDate validUntil,
    String notes
) {
}
