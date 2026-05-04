package com.bioqc.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AiAnalysisRequest(
    @NotBlank String prompt,
    String context,
    String area,
    String examName,
    Integer days
) {
}
