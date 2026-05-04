package com.bioqc.repository;

import com.bioqc.entity.ReportAuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportAuditLogRepository extends JpaRepository<ReportAuditLog, UUID> {
}
