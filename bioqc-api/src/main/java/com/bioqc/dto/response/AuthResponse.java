package com.bioqc.dto.response;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    UserResponse user
) {
}
