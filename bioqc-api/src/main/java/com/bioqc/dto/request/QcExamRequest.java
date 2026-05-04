package com.bioqc.dto.request;

import jakarta.validation.constraints.NotBlank;

public record QcExamRequest(
    @NotBlank String name,
    @NotBlank String area,
    String unit
) {
}
