package com.bioqc.repository;

import com.bioqc.entity.WestgardViolation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WestgardViolationRepository extends JpaRepository<WestgardViolation, UUID> {

    List<WestgardViolation> findByQcRecordId(UUID qcRecordId);

    @Query("""
        SELECT w FROM WestgardViolation w
        JOIN FETCH w.qcRecord qr
        WHERE w.severity = 'REJECTION'
          AND w.createdAt >= :start
        ORDER BY w.createdAt DESC
        """)
    List<WestgardViolation> findRecentRejections(@Param("start") Instant start);

    @Query("""
        SELECT COUNT(DISTINCT w.qcRecord.id) FROM WestgardViolation w
        WHERE w.severity = 'REJECTION'
          AND w.createdAt >= :start
        """)
    long countDistinctRejectedRecords(@Param("start") Instant start);

    /**
     * T5 — query janelada para reports V2. Substitui o antigo
     * {@code findAll().stream().filter(...)} que carregava o universo inteiro.
     *
     * <p><b>Importante</b>: passe {@code area} ja em lowercase
     * ({@code String.toLowerCase(Locale.ROOT)}) — evitamos {@code LOWER(:param)}
     * no JPQL porque PostgreSQL infere {@code bytea} para parametros nao
     * tipados em {@code LOWER()}, quebrando com
     * {@code function lower(bytea) does not exist}. Mesmo padrao ja adotado em
     * {@link MaintenanceRecordRepository#findInPeriod} e
     * {@link PostCalibrationRecordRepository#findByQcRecordAreaAndDateRange}.
     *
     * @param area  area normalizada em lowercase; null traz todas
     * @param start data inicial inclusiva (coluna QcRecord.date)
     * @param end   data final inclusiva
     */
    @Query("""
        SELECT w FROM WestgardViolation w
        JOIN FETCH w.qcRecord qr
        WHERE qr.date BETWEEN :start AND :end
          AND (:area IS NULL OR LOWER(qr.area) = :area)
        ORDER BY qr.date DESC
        """)
    List<WestgardViolation> findByAreaAndPeriod(
        @Param("area") String area,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );
}
