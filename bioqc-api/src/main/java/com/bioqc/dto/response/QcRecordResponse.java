package com.bioqc.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QcRecordResponse(
    UUID id,
    UUID referenceId,
    String examName,
    String area,
    LocalDate date,
    String level,
    String lotNumber,
    Double value,
    Double targetValue,
    Double targetSd,
    Double cv,
    Double cvLimit,
    Double zScore,
    String equipment,
    String analyst,
    String status,
    Boolean needsCalibration,
    List<ViolationResponse> violations,
    Instant createdAt,
    Instant updatedAt,
    String referenceWarning,
    Double postCalibrationValue,
    Double postCalibrationCv,
    String postCalibrationStatus
) {
}
