package com.bioqc.controller;

import com.bioqc.dto.request.QcReferenceRequest;
import com.bioqc.dto.response.QcReferenceResponse;
import com.bioqc.service.QcReferenceService;
import com.bioqc.util.ResponseMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/qc/references")
public class QcReferenceController {

    private final QcReferenceService qcReferenceService;

    public QcReferenceController(QcReferenceService qcReferenceService) {
        this.qcReferenceService = qcReferenceService;
    }

    @GetMapping
    public ResponseEntity<List<QcReferenceResponse>> getReferences(
        @RequestParam(required = false) UUID examId,
        @RequestParam(required = false) Boolean activeOnly
    ) {
        List<QcReferenceResponse> responses = qcReferenceService.getReferences(examId, activeOnly)
            .stream()
            .map(ResponseMapper::toQcReferenceResponse)
            .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/last")
    public ResponseEntity<QcReferenceResponse> getLastReference(
        @RequestParam UUID examId,
        @RequestParam(defaultValue = "Normal") String level
    ) {
        return qcReferenceService.getReferences(examId, false).stream()
            .filter(ref -> ref.getLevel().equalsIgnoreCase(level))
            .max(java.util.Comparator.comparing(
                ref -> ref.getValidFrom() != null ? ref.getValidFrom() : java.time.LocalDate.MIN
            ))
            .map(ResponseMapper::toQcReferenceResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<QcReferenceResponse> createReference(@Valid @RequestBody QcReferenceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ResponseMapper.toQcReferenceResponse(qcReferenceService.createReference(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QcReferenceResponse> updateReference(
        @PathVariable UUID id,
        @Valid @RequestBody QcReferenceRequest request
    ) {
        return ResponseEntity.ok(ResponseMapper.toQcReferenceResponse(qcReferenceService.updateReference(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReference(@PathVariable UUID id) {
        qcReferenceService.deleteReference(id);
        return ResponseEntity.noContent().build();
    }
}
