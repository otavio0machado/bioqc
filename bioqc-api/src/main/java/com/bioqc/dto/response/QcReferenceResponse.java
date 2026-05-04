package com.bioqc.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record QcReferenceResponse(
    UUID id,
    QcExamInfo exam,
    String name,
    String level,
    String lotNumber,
    String manufacturer,
    Double targetValue,
    Double targetSd,
    Double cvMaxThreshold,
    LocalDate validFrom,
    LocalDate validUntil,
    Boolean isActive,
    String notes
) {
    public record QcExamInfo(
        UUID id,
        String name,
        String area,
        String unit,
        Boolean isActive
    ) {}
}
