package com.bioqc.dto.response;

public record ViolationResponse(
    String rule,
    String description,
    String severity
) {
}
