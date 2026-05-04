package com.bioqc.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceResponse(
    UUID id,
    String equipment,
    String type,
    LocalDate date,
    LocalDate nextDate,
    String technician,
    String notes,
    Instant createdAt
) {}
