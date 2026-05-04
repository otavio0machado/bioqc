package com.bioqc.dto.response;

import java.util.List;

public record DashboardAlertsResponse(
    AlertSection<ReagentLotResponse> expiringReagents,
    AlertSection<MaintenanceResponse> pendingMaintenances,
    AlertSection<QcRecordResponse> westgardViolations
) {
    public record AlertSection<T>(long count, List<T> items) {
    }
}
