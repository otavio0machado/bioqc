package com.bioqc.dto.response;

public record DashboardKpiResponse(
    long totalToday,
    long totalMonth,
    double approvalRate,
    boolean hasAlerts,
    long alertsCount
) {
}
