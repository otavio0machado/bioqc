package com.bioqc.repository;

import com.bioqc.entity.PostCalibrationRecord;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostCalibrationRecordRepository extends JpaRepository<PostCalibrationRecord, UUID> {

    Optional<PostCalibrationRecord> findByQcRecordId(UUID qcRecordId);

    List<PostCalibrationRecord> findByQcRecord_IdIn(Collection<UUID> qcRecordIds);

    /**
     * <b>Importante</b>: passe {@code area} ja em lowercase
     * ({@code String.toLowerCase(Locale.ROOT)}) — evitamos {@code LOWER(:param)}
     * no JPQL porque PostgreSQL infere tipo {@code bytea} para parametros
     * nao tipados em {@code LOWER()}, quebrando com {@code function lower(bytea)
     * does not exist}.
     */
    @Query("""
        SELECT p FROM PostCalibrationRecord p
        WHERE LOWER(p.qcRecord.area) = :area
          AND p.qcRecord.date BETWEEN :startDate AND :endDate
        """)
    List<PostCalibrationRecord> findByQcRecordAreaAndDateRange(
        @Param("area") String area,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
