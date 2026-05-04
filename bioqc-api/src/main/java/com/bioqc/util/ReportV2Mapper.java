package com.bioqc.util;

import com.bioqc.dto.reports.v2.ReportDefinitionResponse;
import com.bioqc.dto.reports.v2.ReportExecutionResponse;
import com.bioqc.entity.ReportRun;
import com.bioqc.service.reports.v2.catalog.ReportDefinition;
import com.bioqc.service.reports.v2.catalog.ReportFilterField;
import com.bioqc.service.reports.v2.catalog.ReportFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapeamentos dedicados a Reports V2. Centralizado aqui para nao poluir
 * {@link ResponseMapper} — que segue cuidando do V1.
 */
public final class ReportV2Mapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private ReportV2Mapper() {}

    public static ReportDefinitionResponse toResponse(ReportDefinition def) {
        List<ReportDefinitionResponse.FilterFieldDto> fields = def.filterSpec().fields().stream()
            .map(ReportV2Mapper::toFilterFieldDto)
            .collect(Collectors.toUnmodifiableList());

        return new ReportDefinitionResponse(
            def.code().name(),
            def.name(),
            def.description(),
            def.subtitle(),
            def.icon(),
            def.category().name(),
            def.supportedFormats().stream().map(ReportFormat::name).collect(Collectors.toUnmodifiableSet()),
            fields,
            Set.copyOf(def.roleAccess()),
            def.signatureRequired(),
            def.previewSupported(),
            def.aiCommentaryCapable(),
            def.retentionDays(),
            def.legalBasis()
        );
    }

    public static ReportExecutionResponse toResponse(ReportRun run, String publicBaseUrl) {
        return toResponse(run, publicBaseUrl, List.of());
    }

    /**
     * Variante que aceita warnings gerados no momento da execucao (nao
     * persistidos em ReportRun). Usado no retorno de {@code /generate} para
     * o pacote regulatorio informar quais secoes subordinadas falharam.
     */
    public static ReportExecutionResponse toResponse(ReportRun run, String publicBaseUrl, List<String> warnings) {
        String downloadUrl = "/api/reports/v2/executions/" + run.getId() + "/download";
        String verifyUrl = null;
        if (run.getSha256() != null && publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
            verifyUrl = base + "/r/verify/" + run.getSha256();
        }
        // periodLabel nao esta serializado em ReportRun diretamente — derivado de filters/period; omitido por ora
        String periodLabel = derivePeriodLabel(run);

        return new ReportExecutionResponse(
            run.getId(),
            run.getReportCode(),
            run.getFormat(),
            run.getStatus(),
            run.getReportNumber(),
            run.getSha256(),
            run.getSignatureHash(),
            // signedSha256: alias explicito de signatureHash para contratos novos
            run.getSignatureHash(),
            run.getSizeBytes(),
            run.getPageCount(),
            run.getUsername(),
            run.getCreatedAt(),
            run.getSignedAt(),
            run.getExpiresAt(),
            downloadUrl,
            verifyUrl,
            periodLabel,
            parseLabels(run.getLabels()),
            mergeWarnings(run.getWarnings(), warnings)
        );
    }

    /**
     * Converte o CSV armazenado em {@code report_runs.labels} em {@code List<String>}.
     * Valores em branco e duplicados sao filtrados. A lista retornada e ordenada
     * para produzir respostas deterministicas.
     */
    public static List<String> parseLabels(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<String> items = new ArrayList<>();
        java.util.Set<String> seen = new java.util.TreeSet<>();
        for (String part : csv.split(",")) {
            String trimmed = part == null ? "" : part.trim();
            if (trimmed.isEmpty()) continue;
            seen.add(trimmed);
        }
        items.addAll(seen);
        return List.copyOf(items);
    }

    /**
     * Serializa lista de labels em CSV ordenado e deduplicado.
     * Utilizado por {@code ReportServiceV2.setLabels}.
     */
    public static String serializeLabels(List<String> labels) {
        if (labels == null || labels.isEmpty()) return null;
        java.util.Set<String> deduped = new java.util.TreeSet<>();
        for (String l : labels) {
            if (l == null) continue;
            String trimmed = l.trim();
            if (!trimmed.isEmpty()) deduped.add(trimmed);
        }
        if (deduped.isEmpty()) return null;
        return String.join(",", deduped);
    }

    private static ReportDefinitionResponse.FilterFieldDto toFilterFieldDto(ReportFilterField f) {
        return new ReportDefinitionResponse.FilterFieldDto(
            f.key(),
            f.type().name(),
            f.required(),
            f.allowedValues(),
            f.label(),
            f.helpText()
        );
    }

    private static String derivePeriodLabel(ReportRun run) {
        // best-effort: se periodType/month/year estao preenchidos, monta label basico
        if (run.getPeriodType() == null) return null;
        return switch (run.getPeriodType()) {
            case "year" -> run.getYear() != null ? "Ano " + run.getYear() : null;
            case "specific-month" -> {
                if (run.getMonth() != null && run.getYear() != null) {
                    yield run.getMonth() + "/" + run.getYear();
                }
                yield null;
            }
            default -> null;
        };
    }

    private static List<String> mergeWarnings(String persistedWarnings, List<String> transientWarnings) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        merged.addAll(parseWarnings(persistedWarnings));
        if (transientWarnings != null) {
            for (String warning : transientWarnings) {
                if (warning != null && !warning.isBlank()) {
                    merged.add(warning.trim());
                }
            }
        }
        return List.copyOf(merged);
    }

    private static List<String> parseWarnings(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> parsed = OBJECT_MAPPER.readValue(json, STRING_LIST);
            List<String> warnings = new ArrayList<>();
            for (String item : parsed) {
                if (item != null && !item.isBlank()) warnings.add(item.trim());
            }
            return List.copyOf(warnings);
        } catch (IOException ex) {
            return List.of(json);
        }
    }
}
