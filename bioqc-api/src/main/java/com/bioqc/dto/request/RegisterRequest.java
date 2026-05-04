package com.bioqc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 120) String username,
    @NotBlank
    @Size(min = 4, max = 120)
    String password,
    @NotBlank @Size(max = 120) String name,
    @NotBlank
    @Pattern(regexp = "^(ADMIN|FUNCIONARIO|VIGILANCIA_SANITARIA|VISUALIZADOR)$", message = "Role inválida")
    String role,
    @Size(max = 120) String email
) {
}
