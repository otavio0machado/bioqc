package com.bioqc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Uma execucao do gerador de relatorios ({@code /api/reports/*} V1 e
 * {@code /api/reports/v2/*} V2).
 *
 * Fica fora do fluxo quente: o controller grava em sucesso ou falha com o
 * minimo de info para auditoria, historico e comprovacao de quem/quando gerou
 * o PDF com qual hash. Campos introduzidos pelo Reports V2 sao todos nullable
 * para nao impactar V1 — toda a logica herdada continua preenchendo somente
 * o subset V1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "report_runs")
public class ReportRun {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(length = 64)
    private String area;

    @Column(name = "period_type", length = 16)
    private String periodType;

    @Column(name = "month")
    private Integer month;

    @Column(name = "year")
    private Integer year;

    @Column(name = "report_number", length = 64)
    private String reportNumber;

    @Column(length = 128)
    private String sha256;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "user_id")
    private UUID userId;

    @Column(length = 128)
    private String username;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ---------- Reports V2 (nullable, feature-flagged) ----------

    /**
     * Codigo do relatorio V2 (ex.: CQ_OPERATIONAL_V2). Permanece nulo para
     * execucoes V1 (que usam apenas {@link #type}).
     */
    @Column(name = "report_code", length = 64)
    private String reportCode;

    /**
     * Formato de saida V2 (PDF, HTML, XLSX). Nulo em V1.
     */
    @Column(name = "format", length = 16)
    private String format;

    /**
     * Snapshot dos filtros aplicados, serializado como JSON string. Em Postgres
     * poderia ser JSONB, mas mantemos TEXT para compatibilidade com H2 local.
     */
    @Column(name = "filters", columnDefinition = "TEXT")
    private String filters;

    @Column(name = "storage_key", length = 512)
    private String storageKey;

    /**
     * Storage key do artefato apos estampa de assinatura. Permanece null ate
     * que {@code /sign} seja chamado. O artefato original continua acessivel
     * via {@link #storageKey} e o hash original ({@link #sha256}) segue
     * valido — preservando a cadeia de custodia (Ressalva 1).
     */
    @Column(name = "signed_storage_key", length = 512)
    private String signedStorageKey;

    @Column(name = "signed_by")
    private UUID signedBy;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "signature_hash", length = 128)
    private String signatureHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "share_token", length = 64)
    private String shareToken;

    /**
     * Rotulos livres (tags) do relatorio, serializadas como JSON string.
     * Postgres poderia usar TEXT[], mas TEXT serializado facilita compat com H2.
     */
    @Column(name = "labels", columnDefinition = "TEXT")
    private String labels;

    /**
     * Avisos estruturados da geracao V2, serializados como JSON string.
     * Usado para preservar pacote parcial, ausencia de dados e ressalvas que
     * nao devem sumir apos o response imediato de /generate.
     */
    @Column(name = "warnings", columnDefinition = "TEXT")
    private String warnings;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "request_id", length = 64)
    private String requestId;
}
