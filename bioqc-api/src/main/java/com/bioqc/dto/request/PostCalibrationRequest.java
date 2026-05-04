package com.bioqc.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PostCalibrationRequest(
    @NotNull LocalDate date,
    @NotNull Double postCalibrationValue,
    String analyst,
    String notes
) {
}
