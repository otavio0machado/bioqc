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
 * Uma execucao de importacao em lote ({@code /api/qc-records/batch}).
 *
 * Guarda contagens (total, sucesso, falha), modo (ATOMIC/PARTIAL) e quem
 * disparou, para permitir reprocessamento e auditoria. O detalhe linha-a-linha
 * fica na resposta HTTP; aqui guardamos so o resumo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "import_runs")
public class ImportRun {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(nullable = false, length = 16)
    private String mode;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows;

    @Column(name = "success_rows", nullable = false)
    private Integer successRows;

    @Column(name = "failure_rows", nullable = false)
    private Integer failureRows;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "user_id")
    private UUID userId;

    @Column(length = 128)
    private String username;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
