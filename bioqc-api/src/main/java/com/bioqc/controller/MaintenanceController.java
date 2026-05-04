package com.bioqc.controller;

import com.bioqc.dto.request.MaintenanceRequest;
import com.bioqc.dto.response.MaintenanceResponse;
import com.bioqc.service.MaintenanceService;
import com.bioqc.util.ResponseMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    public ResponseEntity<List<MaintenanceResponse>> getRecords(@RequestParam(required = false) String equipment) {
        List<MaintenanceResponse> responses = maintenanceService.getRecords(equipment)
            .stream()
            .map(ResponseMapper::toMaintenanceResponse)
            .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<MaintenanceResponse> createRecord(@Valid @RequestBody MaintenanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ResponseMapper.toMaintenanceResponse(maintenanceService.createRecord(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<MaintenanceResponse> updateRecord(
        @PathVariable UUID id,
        @Valid @RequestBody MaintenanceRequest request
    ) {
        return ResponseEntity.ok(ResponseMapper.toMaintenanceResponse(maintenanceService.updateRecord(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<Void> deleteRecord(@PathVariable UUID id) {
        maintenanceService.deleteRecord(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pending")
    public ResponseEntity<List<MaintenanceResponse>> getPendingMaintenances() {
        List<MaintenanceResponse> responses = maintenanceService.getPendingMaintenances()
            .stream()
            .map(ResponseMapper::toMaintenanceResponse)
            .toList();
        return ResponseEntity.ok(responses);
    }
}
