package com.bioqc.repository;

import com.bioqc.entity.LabReportEmail;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabReportEmailRepository extends JpaRepository<LabReportEmail, UUID> {
    List<LabReportEmail> findByIsActiveTrueOrderByEmailAsc();
}
