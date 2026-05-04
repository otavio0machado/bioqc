package com.bioqc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(min = 3, max = 120) String username,
    @NotBlank @Size(min = 6, max = 120) String password
) {
}
