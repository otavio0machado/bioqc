package com.bioqc.controller;

import com.bioqc.dto.request.PostCalibrationRequest;
import com.bioqc.dto.request.QcRecordRequest;
import com.bioqc.dto.response.BatchImportResult;
import com.bioqc.dto.response.ImportRunResponse;
import com.bioqc.dto.response.LeveyJenningsResponse;
import com.bioqc.dto.response.QcRecordResponse;
import com.bioqc.entity.PostCalibrationRecord;
import com.bioqc.service.ImportRunService;
import com.bioqc.service.PostCalibrationService;
import com.bioqc.service.QcBatchImportService;
import com.bioqc.service.QcService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qc/records")
public class QcRecordController {

    private final QcService qcService;
    private final PostCalibrationService postCalibrationService;
    private final QcBatchImportService qcBatchImportService;
    private final ImportRunService importRunService;

    public QcRecordController(
        QcService qcService,
        PostCalibrationService postCalibrationService,
        QcBatchImportService qcBatchImportService,
        ImportRunService importRunService
    ) {
        this.qcService = qcService;
        this.postCalibrationService = postCalibrationService;
        this.qcBatchImportService = qcBatchImportService;
        this.importRunService = importRunService;
    }

    @GetMapping
    public ResponseEntity<List<QcRecordResponse>> getRecords(
        @RequestParam(required = false) String area,
        @RequestParam(required = false) String examName,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(qcService.getRecords(area, examName, startDate, endDate));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<QcRecordResponse> createRecord(@Valid @RequestBody QcRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(qcService.createRecord(request));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<List<QcRecordResponse>> createRecordsBatch(
        @Valid @RequestBody List<QcRecordRequest> requests
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(qcService.createRecordsBatch(requests));
    }

    /**
     * Importacao em lote com feedback linha-a-linha e auditoria (ImportRun).
     *
     * Modo PARTIAL (default): linhas invalidas nao bloqueiam as validas.
     * Modo ATOMIC: comportamento legado — qualquer falha aborta o lote inteiro.
     */
    @PostMapping("/batch-v2")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<BatchImportResult> createRecordsBatchV2(
        @RequestBody List<QcRecordRequest> requests,
        @RequestParam(required = false, defaultValue = "partial") String mode,
        Authentication authentication
    ) {
        BatchImportResult result = "atomic".equalsIgnoreCase(mode)
            ? qcBatchImportService.importAtomic(requests, authentication)
            : qcBatchImportService.importPartial(requests, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/import-history")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIGILANCIA_SANITARIA') or hasRole('FUNCIONARIO')")
    public ResponseEntity<List<ImportRunResponse>> importHistory(
        @RequestParam(required = false, defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(importRunService.history(limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<QcRecordResponse> getRecord(@PathVariable UUID id) {
        return ResponseEntity.ok(qcService.getRecord(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<QcRecordResponse> updateRecord(
        @PathVariable UUID id,
        @Valid @RequestBody QcRecordRequest request
    ) {
        return ResponseEntity.ok(qcService.updateRecord(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<Void> deleteRecord(@PathVariable UUID id) {
        qcService.deleteRecord(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        return ResponseEntity.ok(qcService.getStatisticsToday());
    }

    @GetMapping("/levey-jennings")
    public ResponseEntity<List<LeveyJenningsResponse>> getLeveyJenningsData(
        @RequestParam String examName,
        @RequestParam String level,
        @RequestParam String area,
        @RequestParam(required = false) Integer days
    ) {
        return ResponseEntity.ok(qcService.getLeveyJenningsData(examName, level, area, days));
    }

    @GetMapping("/{id}/post-calibration")
    public ResponseEntity<PostCalibrationRecord> getPostCalibration(@PathVariable UUID id) {
        return postCalibrationService.getByQcRecord(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/post-calibration")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<PostCalibrationRecord> createPostCalibration(
        @PathVariable UUID id,
        @Valid @RequestBody PostCalibrationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postCalibrationService.createPostCalibration(id, request));
    }
}
