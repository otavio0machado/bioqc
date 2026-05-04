package com.bioqc.repository;

import com.bioqc.entity.QcExam;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QcExamRepository extends JpaRepository<QcExam, UUID> {

    List<QcExam> findByAreaAndIsActiveTrue(String area);

    List<QcExam> findByIsActiveTrue();
}
