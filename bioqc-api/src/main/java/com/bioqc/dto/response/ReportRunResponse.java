package com.bioqc.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ReportRunResponse(
    UUID id,
    String type,
    String area,
    String periodType,
    Integer month,
    Integer year,
    String reportNumber,
    String sha256,
    Long sizeBytes,
    Long durationMs,
    String status,
    String errorMessage,
    String username,
    Instant createdAt
) {
}
