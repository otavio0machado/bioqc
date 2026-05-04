package com.bioqc.dto.response;

import java.time.LocalDate;

public record LeveyJenningsResponse(
    LocalDate date,
    Double value,
    Double target,
    Double sd,
    Double cv,
    String status,
    Double zScore,
    Double upper2sd,
    Double lower2sd,
    Double upper3sd,
    Double lower3sd
) {
}
