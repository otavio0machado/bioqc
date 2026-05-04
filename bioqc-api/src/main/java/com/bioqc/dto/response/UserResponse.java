package com.bioqc.dto.response;

import java.util.List;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String username,
    String email,
    String name,
    String role,
    Boolean isActive,
    List<String> permissions
) {
}
