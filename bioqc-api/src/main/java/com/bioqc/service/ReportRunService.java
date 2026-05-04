package com.bioqc.service;

import com.bioqc.dto.response.ReportRunResponse;
import com.bioqc.entity.ReportRun;
import com.bioqc.repository.ReportRunRepository;
import com.bioqc.service.reports.v2.ReportSigner;
import com.bioqc.service.reports.v2.catalog.ReportDefinition;
import com.bioqc.service.reports.v2.catalog.ReportFormat;
import com.bioqc.service.reports.v2.generator.GenerationContext;
import com.bioqc.service.reports.v2.generator.ReportArtifact;
import com.bioqc.util.ResponseMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço de auditoria para geracao de relatorios. Grava uma linha por chamada
 * aos endpoints de {@code /api/reports/*} — em sucesso ou em falha — e expoe
 * o historico para a aba Relatorios do frontend.
 */
@Service
public class ReportRunService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportRunService.class);

    public static final String TYPE_QC_PDF = "QC_PDF";
    public static final String TYPE_REAGENTS_PDF = "REAGENTS_PDF";
    /** Tipo generico usado pelas execucoes V2; o codigo fino fica em {@code reportCode}. */
    public static final String TYPE_V2 = "V2";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILURE = "FAILURE";
    public static final String STATUS_WITH_WARNINGS = "WITH_WARNINGS";
    public static final String STATUS_SIGNED = "SIGNED";

    private final ReportRunRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportRunService(ReportRunRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void recordSuccess(
        String type, String area, String periodType, Integer month, Integer year,
        String reportNumber, String sha256, long sizeBytes, long durationMs,
        Authentication authentication
    ) {
        repository.save(ReportRun.builder()
            .id(UUID.randomUUID())
            .type(type)
            .area(area)
            .periodType(periodType)
            .month(month)
            .year(year)
            .reportNumber(reportNumber)
            .sha256(sha256)
            .sizeBytes(sizeBytes)
            .durationMs(durationMs)
            .status(STATUS_SUCCESS)
            .username(usernameOf(authentication))
            .build());
    }

    @Transactional
    public void recordFailure(
        String type, String area, String periodType, Integer month, Integer year,
        long durationMs, String errorMessage, Authentication authentication
    ) {
        repository.save(ReportRun.builder()
            .id(UUID.randomUUID())
            .type(type)
            .area(area)
            .periodType(periodType)
            .month(month)
            .year(year)
            .durationMs(durationMs)
            .status(STATUS_FAILURE)
            .errorMessage(truncate(errorMessage, 4_000))
            .username(usernameOf(authentication))
            .build());
    }

    @Transactional(readOnly = true)
    public List<ReportRunResponse> history(int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 200);
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit)).stream()
            .map(ResponseMapper::toReportRunResponse)
            .toList();
    }

    private String usernameOf(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    // ---------- Reports V2 ----------

    /**
     * Registra uma execucao V2 bem-sucedida. Preenche todas as colunas novas
     * (report_code, format, filters, storage_key, expires_at, correlation_id,
     * request_id) alem das herdadas de V1 para manter a view de historico
     * unificada.
     */
    @Transactional
    public ReportRun recordSuccessV2(
        ReportArtifact artifact,
        ReportDefinition definition,
        String storageKey,
        GenerationContext ctx,
        Map<String, Object> filters,
        ReportFormat format
    ) {
        String filtersJson = serializeFilters(filters);
        String warningsJson = serializeWarnings(artifact.warnings());
        Instant expiresAt = ctx.now().plus(definition.retentionDays(), ChronoUnit.DAYS);
        String status = artifact.warnings().isEmpty() ? STATUS_SUCCESS : STATUS_WITH_WARNINGS;

        ReportRun run = ReportRun.builder()
            .id(UUID.randomUUID())
            .type(TYPE_V2)
            .reportCode(definition.code().name())
            .format(format.name())
            .filters(filtersJson)
            .storageKey(storageKey)
            .reportNumber(artifact.reportNumber())
            .sha256(artifact.sha256())
            .sizeBytes(artifact.sizeBytes())
            .pageCount(artifact.pageCount() > 0 ? artifact.pageCount() : null)
            .status(status)
            .warnings(warningsJson)
            .userId(ctx.userId())
            .username(ctx.username())
            .expiresAt(expiresAt)
            .correlationId(ctx.correlationId())
            .requestId(ctx.requestId())
            // V1-compat: preenche area a partir do filtro 'area' se presente
            .area(filters == null ? null : asStringOrNull(filters.get("area")))
            .periodType(filters == null ? null : asStringOrNull(filters.get("periodType")))
            .month(filters == null ? null : asIntegerOrNull(filters.get("month")))
            .year(filters == null ? null : asIntegerOrNull(filters.get("year")))
            .createdAt(ctx.now())
            .build();

        return repository.save(run);
    }

    @Transactional
    public ReportRun recordFailureV2(
        ReportDefinition definition,
        GenerationContext ctx,
        Map<String, Object> filters,
        ReportFormat format,
        String errorMessage
    ) {
        String filtersJson = serializeFilters(filters);
        ReportRun run = ReportRun.builder()
            .id(UUID.randomUUID())
            .type(TYPE_V2)
            .reportCode(definition.code().name())
            .format(format == null ? null : format.name())
            .filters(filtersJson)
            .status(STATUS_FAILURE)
            .userId(ctx.userId())
            .username(ctx.username())
            .correlationId(ctx.correlationId())
            .requestId(ctx.requestId())
            .errorMessage(truncate(errorMessage, 4_000))
            .area(filters == null ? null : asStringOrNull(filters.get("area")))
            .periodType(filters == null ? null : asStringOrNull(filters.get("periodType")))
            .month(filters == null ? null : asIntegerOrNull(filters.get("month")))
            .year(filters == null ? null : asIntegerOrNull(filters.get("year")))
            .build();
        return repository.save(run);
    }

    /**
     * Atualiza uma execucao existente com os dados de assinatura. Usado pelo
     * endpoint {@code /sign}. Muda o status para {@link #STATUS_SIGNED} para
     * que a UI consiga distinguir facilmente.
     *
     * <p>Importante (Ressalva 1): {@code signedStorageKey} e diferente de
     * {@link ReportRun#storageKey} para que o artefato original permaneca
     * imutavel e o par ({@code sha256}, {@code signatureHash}) seja coerente
     * com os bytes retornados pelo {@code /download}.
     */
    @Transactional
    public ReportRun recordSigned(
        UUID runId,
        ReportSigner.SignatureResult signatureResult,
        UUID signerUserId,
        String signedStorageKey
    ) {
        ReportRun run = repository.findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("ReportRun nao encontrado: " + runId));
        run.setSignedBy(signerUserId);
        run.setSignedAt(signatureResult.signedAt());
        run.setSignatureHash(signatureResult.signatureHash());
        run.setSignedStorageKey(signedStorageKey);
        run.setStatus(STATUS_SIGNED);
        return repository.save(run);
    }

    private String serializeFilters(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(filters);
        } catch (JsonProcessingException ex) {
            LOG.warn("Falha ao serializar filtros V2 para JSON — gravando null", ex);
            return null;
        }
    }

    private String serializeWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(warnings);
        } catch (JsonProcessingException ex) {
            LOG.warn("Falha ao serializar warnings V2 para JSON — gravando null", ex);
            return null;
        }
    }

    private static String asStringOrNull(Object o) {
        return o == null ? null : o.toString();
    }

    private static Integer asIntegerOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Integer i) return i;
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ex) { return null; }
        }
        return null;
    }
}
