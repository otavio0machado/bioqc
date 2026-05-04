package com.bioqc.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VoiceFormRequest(
    @NotBlank String audioBase64,
    @NotBlank String formType,
    String mimeType
) {
}
