package com.bioqc.service;

import com.bioqc.dto.response.ImportRunResponse;
import com.bioqc.entity.ImportRun;
import com.bioqc.repository.ImportRunRepository;
import com.bioqc.util.ResponseMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auditoria de importacoes em lote. Grava uma {@code ImportRun} por chamada
 * (modo ATOMIC ou PARTIAL) e expoe o historico para o frontend.
 */
@Service
public class ImportRunService {

    public static final String SOURCE_QC_RECORDS = "QC_RECORDS";
    public static final String MODE_ATOMIC = "ATOMIC";
    public static final String MODE_PARTIAL = "PARTIAL";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILURE = "FAILURE";

    private final ImportRunRepository repository;

    public ImportRunService(ImportRunRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ImportRun record(
        String source, String mode,
        int total, int success, int failure, long durationMs,
        String errorSummary, Authentication authentication
    ) {
        String status;
        if (failure == 0 && total > 0) {
            status = STATUS_SUCCESS;
        } else if (success == 0) {
            status = STATUS_FAILURE;
        } else {
            status = STATUS_PARTIAL;
        }
        ImportRun run = ImportRun.builder()
            .id(UUID.randomUUID())
            .source(source)
            .mode(mode)
            .totalRows(total)
            .successRows(success)
            .failureRows(failure)
            .durationMs(durationMs)
            .status(status)
            .errorSummary(truncate(errorSummary, 4_000))
            .username(authentication == null ? null : authentication.getName())
            .build();
        return repository.save(run);
    }

    @Transactional(readOnly = true)
    public List<ImportRunResponse> history(int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 200);
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit)).stream()
            .map(ResponseMapper::toImportRunResponse)
            .toList();
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
