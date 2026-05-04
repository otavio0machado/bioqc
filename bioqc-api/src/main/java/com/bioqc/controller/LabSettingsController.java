package com.bioqc.controller;

import com.bioqc.dto.request.LabReportEmailRequest;
import com.bioqc.dto.request.LabSettingsRequest;
import com.bioqc.dto.response.LabReportEmailResponse;
import com.bioqc.dto.response.LabSettingsResponse;
import com.bioqc.service.LabSettingsService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lab-settings")
public class LabSettingsController {

    private final LabSettingsService labSettingsService;

    public LabSettingsController(LabSettingsService labSettingsService) {
        this.labSettingsService = labSettingsService;
    }

    @GetMapping
    public ResponseEntity<LabSettingsResponse> getSettings() {
        return ResponseEntity.ok(labSettingsService.getSettings());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LabSettingsResponse> updateSettings(@Valid @RequestBody LabSettingsRequest request) {
        return ResponseEntity.ok(labSettingsService.updateSettings(request));
    }

    @GetMapping("/emails")
    public ResponseEntity<List<LabReportEmailResponse>> listEmails() {
        return ResponseEntity.ok(labSettingsService.listEmails());
    }

    @PostMapping("/emails")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LabReportEmailResponse> addEmail(@Valid @RequestBody LabReportEmailRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(labSettingsService.addEmail(request));
    }

    @PatchMapping("/emails/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LabReportEmailResponse> toggleEmail(
        @PathVariable UUID id,
        @RequestParam boolean active
    ) {
        return ResponseEntity.ok(labSettingsService.setEmailActive(id, active));
    }

    @DeleteMapping("/emails/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeEmail(@PathVariable UUID id) {
        labSettingsService.removeEmail(id);
        return ResponseEntity.noContent().build();
    }
}
