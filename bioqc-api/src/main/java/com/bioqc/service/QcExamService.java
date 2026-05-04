package com.bioqc.service;

import com.bioqc.dto.request.QcExamRequest;
import com.bioqc.entity.QcExam;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.QcExamRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QcExamService {

    private final QcExamRepository qcExamRepository;

    public QcExamService(QcExamRepository qcExamRepository) {
        this.qcExamRepository = qcExamRepository;
    }

    @Transactional(readOnly = true)
    public List<QcExam> getExams(String area) {
        if (area == null || area.isBlank()) {
            return qcExamRepository.findByIsActiveTrue();
        }
        return qcExamRepository.findByAreaAndIsActiveTrue(area);
    }

    @Transactional
    public QcExam createExam(QcExamRequest request) {
        return qcExamRepository.save(QcExam.builder()
            .name(request.name())
            .area(request.area())
            .unit(request.unit())
            .isActive(Boolean.TRUE)
            .build());
    }

    @Transactional
    public QcExam updateExam(UUID id, QcExamRequest request) {
        QcExam exam = qcExamRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Exame não encontrado"));
        exam.setName(request.name());
        exam.setArea(request.area());
        exam.setUnit(request.unit());
        return qcExamRepository.save(exam);
    }

    @Transactional
    public void deleteExam(UUID id) {
        if (!qcExamRepository.existsById(id)) {
            throw new ResourceNotFoundException("Exame não encontrado");
        }
        qcExamRepository.deleteById(id);
    }
}
