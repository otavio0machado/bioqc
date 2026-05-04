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
 * Registro append-only de assinaturas eletronicas de laudos. Cada chamada a
 * {@code /api/reports/v2/executions/{id}/sign} grava exatamente uma linha
 * aqui, servindo como cadeia de custodia juridica do par
 * (sha256 original, signatureHash).
 *
 * <p>Esta entidade e intencionalmente imutavel pelo lado aplicacao: o
 * {@code ReportSignatureLogRepository} expoe apenas {@code save(...)} e
 * {@code findBy...}. Nenhum fluxo faz update ou delete. Isto garante que a
 * retencao do {@code ReportRun} (que controla apenas o PDF acessivel via
 * {@code /download}) nao afeta o registro juridico da assinatura.
 *
 * <p>Alinha com o framework regulatorio: RDC ANVISA 786/2023 + ISO 15189:2022
 * exigem registro permanente da responsabilidade tecnica sobre cada laudo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "report_signature_log")
public class ReportSignatureLog {

    @Id
    private UUID id;

    @Column(name = "report_run_id", nullable = false)
    private UUID reportRunId;

    @Column(name = "report_number", nullable = false, length = 30)
    private String reportNumber;

    /** SHA-256 dos bytes do PDF original (pre-assinatura). */
    @Column(name = "original_sha256", nullable = false, length = 64)
    private String originalSha256;

    /** SHA-256 dos bytes do PDF apos a estampa de assinatura. */
    @Column(name = "signature_hash", nullable = false, length = 64)
    private String signatureHash;

    @Column(name = "signed_by_user_id", nullable = false)
    private UUID signedByUserId;

    @Column(name = "signed_by_name", nullable = false, length = 255)
    private String signedByName;

    @Column(name = "signer_registration", nullable = false, length = 100)
    private String signerRegistration;

    @CreationTimestamp
    @Column(name = "signed_at", nullable = false, updatable = false)
    private Instant signedAt;

    /** Storage key do artefato assinado (copia separada do original). */
    @Column(name = "signed_storage_key", nullable = false, length = 512)
    private String signedStorageKey;
}
