package com.bioqc.dto.response;

public record PasswordResetResponse(
    String message,
    String resetUrl
) {
}
