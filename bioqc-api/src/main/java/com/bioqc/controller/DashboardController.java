package com.bioqc.controller;

import com.bioqc.dto.response.DashboardAlertsResponse;
import com.bioqc.dto.response.DashboardKpiResponse;
import com.bioqc.dto.response.QcRecordResponse;
import com.bioqc.service.DashboardService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/kpis")
    public ResponseEntity<DashboardKpiResponse> getKpis(@RequestParam(required = false) String area) {
        return ResponseEntity.ok(dashboardService.getKpis(area));
    }

    @GetMapping("/alerts")
    public ResponseEntity<DashboardAlertsResponse> getAlerts() {
        return ResponseEntity.ok(dashboardService.getAlerts());
    }

    @GetMapping("/recent-records")
    public ResponseEntity<List<QcRecordResponse>> getRecentRecords(@RequestParam(defaultValue = "10") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return ResponseEntity.ok(dashboardService.getRecentRecords(safeLimit));
    }
}
