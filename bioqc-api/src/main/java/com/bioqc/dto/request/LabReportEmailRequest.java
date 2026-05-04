package com.bioqc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LabReportEmailRequest(
    @NotBlank @Email @Size(max = 200) String email,
    @Size(max = 200) String name,
    Boolean isActive
) {
}
