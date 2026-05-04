package com.bioqc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record MaintenanceRequest(
    @NotBlank String equipment,
    @NotBlank String type,
    @NotNull LocalDate date,
    LocalDate nextDate,
    String technician,
    String notes
) {
}
