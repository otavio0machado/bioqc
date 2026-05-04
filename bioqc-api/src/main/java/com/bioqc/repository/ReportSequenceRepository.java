package com.bioqc.repository;

import com.bioqc.entity.ReportSequence;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ReportSequenceRepository extends JpaRepository<ReportSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReportSequence> findByPeriodKey(String periodKey);
}
