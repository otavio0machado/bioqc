package com.bioqc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro append-only de downloads de relatorios V2. Grava quem baixou,
 * quando, qual versao (original pre-assinatura vs signed), IP e user-agent.
 *
 * <p>Motivacao: ISO 15189:2022 item 8.4.1 — rastreabilidade de distribuicao
 * de documentos. Complementa `ReportAuditLog` (que grava emissao) e
 * `ReportSignatureLog` (que grava assinatura).
 *
 * <p>Imutavel: sem setters apos construcao, sem exclusao pela API.
 */
@Entity
@Table(name = "report_download_log")
public class ReportDownloadLog {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "report_run_id", nullable = false)
    private UUID reportRunId;

    @Column(name = "report_number", nullable = false, length = 30)
    private String reportNumber;

    @Column(name = "sha256_served", nullable = false, length = 64)
    private String sha256Served;

    @Column(name = "version_served", nullable = false, length = 16)
    private String versionServed;  // "original" ou "signed"

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "downloaded_by_user_id")
    private UUID downloadedByUserId;

    @Column(name = "downloaded_by_name", length = 255)
    private String downloadedByName;

    @Column(name = "downloaded_at", nullable = false)
    private Instant downloadedAt;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    protected ReportDownloadLog() {
        // JPA
    }

    public ReportDownloadLog(
        UUID id,
        UUID reportRunId,
        String reportNumber,
        String sha256Served,
        String versionServed,
        long sizeBytes,
        UUID downloadedByUserId,
        String downloadedByName,
        Instant downloadedAt,
        String clientIp,
        String userAgent,
        String correlationId
    ) {
        this.id = id;
        this.reportRunId = reportRunId;
        this.reportNumber = reportNumber;
        this.sha256Served = sha256Served;
        this.versionServed = versionServed;
        this.sizeBytes = sizeBytes;
        this.downloadedByUserId = downloadedByUserId;
        this.downloadedByName = downloadedByName;
        this.downloadedAt = downloadedAt;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.correlationId = correlationId;
    }

    public UUID getId() { return id; }
    public UUID getReportRunId() { return reportRunId; }
    public String getReportNumber() { return reportNumber; }
    public String getSha256Served() { return sha256Served; }
    public String getVersionServed() { return versionServed; }
    public long getSizeBytes() { return sizeBytes; }
    public UUID getDownloadedByUserId() { return downloadedByUserId; }
    public String getDownloadedByName() { return downloadedByName; }
    public Instant getDownloadedAt() { return downloadedAt; }
    public String getClientIp() { return clientIp; }
    public String getUserAgent() { return userAgent; }
    public String getCorrelationId() { return correlationId; }
}
