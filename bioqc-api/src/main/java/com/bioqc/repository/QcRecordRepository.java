package com.bioqc.repository;

import com.bioqc.entity.QcRecord;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QcRecordRepository extends JpaRepository<QcRecord, UUID> {

    List<QcRecord> findByAreaOrderByDateDesc(String area);

    List<QcRecord> findByExamNameAndAreaOrderByDateDesc(String examName, String area);

    List<QcRecord> findByDateBetweenAndArea(LocalDate start, LocalDate end, String area);

    long countByDate(LocalDate date);

    long countByDateBetween(LocalDate start, LocalDate end);

    long countByDateAndArea(LocalDate date, String area);

    long countByDateBetweenAndArea(LocalDate start, LocalDate end, String area);

    @Query("""
        SELECT CASE
            WHEN COUNT(q) = 0 THEN 0.0
            ELSE (SUM(CASE WHEN q.status = 'APROVADO' THEN 1.0 ELSE 0.0 END) * 100.0) / COUNT(q)
        END
        FROM QcRecord q
        WHERE q.date BETWEEN :startDate AND :endDate
        """)
    Double calculateApprovalRate(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT CASE
            WHEN COUNT(q) = 0 THEN 0.0
            ELSE (SUM(CASE WHEN q.status = 'APROVADO' THEN 1.0 ELSE 0.0 END) * 100.0) / COUNT(q)
        END
        FROM QcRecord q
        WHERE q.date BETWEEN :startDate AND :endDate
          AND LOWER(q.area) = LOWER(:area)
        """)
    Double calculateApprovalRateByArea(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("area") String area);

    @Query("""
        SELECT q FROM QcRecord q
        WHERE q.examName = :examName
          AND q.level = :level
          AND q.area = :area
        ORDER BY q.date DESC
        """)
    List<QcRecord> findLeveyJenningsData(
        @Param("examName") String examName,
        @Param("level") String level,
        @Param("area") String area,
        Pageable pageable
    );

    @Query("""
        SELECT q FROM QcRecord q
        WHERE q.reference.id = :referenceId
          AND q.examName = :examName
          AND q.level = :level
          AND q.area = :area
          AND q.date <= :referenceDate
          AND (:excludeId IS NULL OR q.id <> :excludeId)
        ORDER BY q.date DESC, q.createdAt DESC
        """)
    List<QcRecord> findWestgardHistory(
        @Param("referenceId") UUID referenceId,
        @Param("examName") String examName,
        @Param("level") String level,
        @Param("area") String area,
        @Param("referenceDate") LocalDate referenceDate,
        @Param("excludeId") UUID excludeId,
        Pageable pageable
    );

    List<QcRecord> findByExamNameAndLevelAndAreaOrderByDateDesc(
        String examName,
        String level,
        String area,
        Pageable pageable
    );

    List<QcRecord> findTop10ByOrderByCreatedAtDesc();

    List<QcRecord> findAllByOrderByDateDesc();

    @Query("""
        SELECT q FROM QcRecord q
        WHERE (:area IS NULL OR :area = '' OR LOWER(q.area) = LOWER(:area))
          AND (:examName IS NULL OR :examName = '' OR LOWER(q.examName) = LOWER(:examName))
          AND (CAST(:startDate AS localdate) IS NULL OR q.date >= :startDate)
          AND (CAST(:endDate AS localdate) IS NULL OR q.date <= :endDate)
        ORDER BY q.date DESC
        """)
    List<QcRecord> findByFilters(
        @Param("area") String area,
        @Param("examName") String examName,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT q FROM QcRecord q
        WHERE LOWER(q.area) = LOWER(:area)
          AND q.date BETWEEN :startDate AND :endDate
        ORDER BY q.date DESC
        """)
    List<QcRecord> findByAreaAndDateRange(
        @Param("area") String area,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Retorna o conjunto distinto de lotNumbers usados em QC dentro de uma janela.
     * Usado em Reagentes Fase 3 para marcar lotes como "ativos em CQ recente".
     * O match e case-insensitive para lidar com divergencias de digitacao.
     */
    @Query("""
        SELECT DISTINCT LOWER(TRIM(q.lotNumber)) FROM QcRecord q
        WHERE q.lotNumber IS NOT NULL
          AND LOWER(TRIM(q.lotNumber)) IN :lotNumbers
          AND q.date >= :since
        """)
    List<String> findActiveLotNumbersSince(
        @Param("lotNumbers") Collection<String> lotNumbers,
        @Param("since") LocalDate since
    );

    @Query("""
        SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END
        FROM QcRecord q
        WHERE q.lotNumber IS NOT NULL
          AND LOWER(TRIM(q.lotNumber)) = LOWER(TRIM(:lotNumber))
        """)
    boolean existsByLotNumberOperational(@Param("lotNumber") String lotNumber);
}
