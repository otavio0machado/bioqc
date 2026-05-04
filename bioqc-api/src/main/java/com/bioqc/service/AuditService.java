package com.bioqc.service;

import com.bioqc.entity.AuditLog;
import com.bioqc.entity.User;
import com.bioqc.repository.AuditLogRepository;
import com.bioqc.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AuditService(
        AuditLogRepository auditLogRepository,
        UserRepository userRepository,
        ObjectMapper objectMapper
    ) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public void log(String action, String entityType, UUID entityId, Map<String, Object> details) {
        try {
            User user = resolveCurrentUser();
            JsonNode detailsNode = details != null
                ? objectMapper.valueToTree(details)
                : objectMapper.createObjectNode();

            auditLogRepository.save(AuditLog.builder()
                .user(user)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(detailsNode)
                .build());
        } catch (Exception exception) {
            log.warn("Falha ao registrar log de auditoria: action={}, entity={}", action, entityType, exception);
        }
    }

    public void log(String action, String entityType, UUID entityId) {
        log(action, entityType, entityId, null);
    }

    public List<AuditLog> getLogsByUser(UUID userId) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<AuditLog> getRecentLogs(int limit) {
        return auditLogRepository.findAll(
            org.springframework.data.domain.PageRequest.of(0, limit,
                org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.DESC, "createdAt"
                ))
        ).getContent();
    }

    private User resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) {
            return null;
        }
        try {
            UUID userId = (UUID) auth.getDetails();
            return userRepository.findById(userId).orElse(null);
        } catch (ClassCastException exception) {
            return null;
        }
    }
}
