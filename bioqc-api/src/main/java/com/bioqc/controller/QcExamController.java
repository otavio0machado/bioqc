package com.bioqc.controller;

import com.bioqc.dto.request.QcExamRequest;
import com.bioqc.entity.QcExam;
import com.bioqc.service.QcExamService;
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
@RequestMapping("/api/qc/exams")
public class QcExamController {

    private final QcExamService qcExamService;

    public QcExamController(QcExamService qcExamService) {
        this.qcExamService = qcExamService;
    }

    @GetMapping
    public ResponseEntity<List<QcExam>> getExams(@RequestParam(required = false) String area) {
        return ResponseEntity.ok(qcExamService.getExams(area));
    }

    @PostMapping
    public ResponseEntity<QcExam> createExam(@Valid @RequestBody QcExamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(qcExamService.createExam(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QcExam> updateExam(@PathVariable UUID id, @Valid @RequestBody QcExamRequest request) {
        return ResponseEntity.ok(qcExamService.updateExam(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExam(@PathVariable UUID id) {
        qcExamService.deleteExam(id);
        return ResponseEntity.noContent().build();
    }
}
