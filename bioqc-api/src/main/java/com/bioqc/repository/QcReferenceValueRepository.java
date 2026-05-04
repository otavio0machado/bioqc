package com.bioqc.repository;

import com.bioqc.entity.QcReferenceValue;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QcReferenceValueRepository extends JpaRepository<QcReferenceValue, UUID> {

    List<QcReferenceValue> findByExamIdAndIsActiveTrue(UUID examId);

    List<QcReferenceValue> findByIsActiveTrue();

    Optional<QcReferenceValue> findByExam_NameAndLevelAndIsActiveTrue(String examName, String level);

    List<QcReferenceValue> findByExam_NameIgnoreCaseAndExam_AreaIgnoreCaseAndLevelIgnoreCaseAndIsActiveTrue(
        String examName,
        String area,
        String level
    );

    @Query("SELECT r FROM QcReferenceValue r WHERE r.exam.id = :examId " +
           "AND LOWER(r.level) = LOWER(:level) AND r.isActive = true " +
           "AND (r.validFrom IS NULL OR r.validFrom <= :validUntil) " +
           "AND (r.validUntil IS NULL OR r.validUntil >= :validFrom) " +
           "AND (:excludeId IS NULL OR r.id <> :excludeId)")
    List<QcReferenceValue> findOverlapping(
        @Param("examId") UUID examId,
        @Param("level") String level,
        @Param("validFrom") LocalDate validFrom,
        @Param("validUntil") LocalDate validUntil,
        @Param("excludeId") UUID excludeId
    );
}
