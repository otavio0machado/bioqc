package com.bioqc.controller;

import com.bioqc.dto.request.AdminResetPasswordRequest;
import com.bioqc.dto.request.AdminUpdateUserRequest;
import com.bioqc.dto.request.AdminUserRequest;
import com.bioqc.dto.response.UserResponse;
import com.bioqc.entity.AuditLog;
import com.bioqc.service.AdminService;
import com.bioqc.service.AuditService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final AuditService auditService;

    public AdminController(AdminService adminService, AuditService auditService) {
        this.adminService = adminService;
        this.auditService = auditService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(adminService.listUsers());
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody AdminUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createUser(request));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<UserResponse> updateUser(
        @PathVariable UUID id,
        @Valid @RequestBody AdminUpdateUserRequest request,
        Authentication authentication
    ) {
        UUID requestingUserId = (UUID) authentication.getDetails();
        return ResponseEntity.ok(adminService.updateUser(id, request, requestingUserId));
    }

    @PutMapping("/users/{id}/password")
    public ResponseEntity<Void> resetPassword(
        @PathVariable UUID id,
        @Valid @RequestBody AdminResetPasswordRequest request
    ) {
        adminService.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<Map<String, Object>>> getAuditLogs(
        @RequestParam(required = false) UUID userId,
        @RequestParam(defaultValue = "100") int limit
    ) {
        List<AuditLog> logs = userId != null
            ? auditService.getLogsByUser(userId)
            : auditService.getRecentLogs(Math.min(limit, 500));

        List<Map<String, Object>> result = logs.stream().map(log -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", log.getId());
            entry.put("action", log.getAction());
            entry.put("entityType", log.getEntityType());
            entry.put("entityId", log.getEntityId());
            entry.put("details", log.getDetails());
            entry.put("createdAt", log.getCreatedAt());
            entry.put("userName", log.getUser() != null ? log.getUser().getName() : null);
            entry.put("username", log.getUser() != null ? log.getUser().getUsername() : null);
            return entry;
        }).toList();

        return ResponseEntity.ok(result);
    }
}
