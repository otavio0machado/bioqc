package com.bioqc.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ImportRunResponse(
    UUID id,
    String source,
    String mode,
    Integer totalRows,
    Integer successRows,
    Integer failureRows,
    Long durationMs,
    String status,
    String errorSummary,
    String username,
    Instant createdAt
) {
}
