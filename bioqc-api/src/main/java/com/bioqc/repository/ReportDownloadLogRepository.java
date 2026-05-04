package com.bioqc.repository;

import com.bioqc.entity.ReportDownloadLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Append-only: sem metodos de delete expostos (auditoria e imutavel).
 * Leitura e permitida para relatorio de quem-baixou-o-que.
 */
public interface ReportDownloadLogRepository extends JpaRepository<ReportDownloadLog, UUID> {

    List<ReportDownloadLog> findByReportRunIdOrderByDownloadedAtDesc(UUID reportRunId);

    long countByReportRunId(UUID reportRunId);
}
