package com.bioqc.repository;

import com.bioqc.entity.MaintenanceRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, UUID> {

    List<MaintenanceRecord> findAllByOrderByDateDesc();

    List<MaintenanceRecord> findByEquipment(String equipment);

    @Query("""
        SELECT m FROM MaintenanceRecord m
        WHERE m.nextDate IS NOT NULL
          AND m.nextDate <= CURRENT_DATE
        ORDER BY m.nextDate ASC
        """)
    List<MaintenanceRecord> findPendingMaintenances();

    @Query("""
        SELECT COUNT(m) FROM MaintenanceRecord m
        WHERE m.nextDate IS NOT NULL
          AND m.nextDate <= CURRENT_DATE
        """)
    long countPendingMaintenances();

    /**
     * Retorna valores distintos de {@code equipment}, ordenados. Usado para
     * autocomplete nos filtros de Reports V2. Alimentado pelo endpoint
     * {@code GET /api/reports/v2/suggestions/equipment} com cache de 5 min.
     */
    @Query("""
        SELECT DISTINCT m.equipment FROM MaintenanceRecord m
        WHERE m.equipment IS NOT NULL AND m.equipment <> ''
        ORDER BY m.equipment
        """)
    List<String> findDistinctEquipments();

    /**
     * Retorna manutencoes no intervalo de datas. Usado pelo generator de
     * MANUTENCAO_KPI.
     *
     * <p><b>Importante</b>: os parametros {@code equipment} e {@code type}
     * devem chegar <em>ja em lowercase</em> (via {@code String.toLowerCase(ROOT)}
     * no caller). Evitamos {@code LOWER(:param)} no JPQL porque PostgreSQL,
     * quando o parametro e {@code null}, infere tipo {@code bytea} por default e
     * lanca {@code function lower(bytea) does not exist}. A query foi simplificada
     * para comparar diretamente o parametro ja-normalizado contra o lowercase
     * da coluna.
     */
    @Query("""
        SELECT m FROM MaintenanceRecord m
        WHERE m.date BETWEEN :start AND :end
          AND (:equipment IS NULL OR LOWER(m.equipment) = :equipment)
          AND (:type IS NULL OR LOWER(m.type) = :type)
        ORDER BY m.date DESC
        """)
    List<MaintenanceRecord> findInPeriod(
        @org.springframework.data.repository.query.Param("start") java.time.LocalDate start,
        @org.springframework.data.repository.query.Param("end") java.time.LocalDate end,
        @org.springframework.data.repository.query.Param("equipment") String equipment,
        @org.springframework.data.repository.query.Param("type") String type
    );

    /**
     * T5 — proximas manutencoes agendadas dentro de uma janela, a partir de
     * {@code today}. Substitui o padrao {@code findAll().stream().filter(...)}
     * usado pelo generator de Manutencao.
     */
    @Query("""
        SELECT m FROM MaintenanceRecord m
        WHERE m.nextDate IS NOT NULL
          AND m.nextDate >= :today
          AND m.nextDate < :limit
        ORDER BY m.nextDate ASC
        """)
    List<MaintenanceRecord> findUpcoming(
        @org.springframework.data.repository.query.Param("today") java.time.LocalDate today,
        @org.springframework.data.repository.query.Param("limit") java.time.LocalDate limit
    );

    /**
     * T5 — manutencoes atrasadas (nextDate &lt; today). Usada em alertas do
     * consolidado multi-area.
     */
    @Query("""
        SELECT m FROM MaintenanceRecord m
        WHERE m.nextDate IS NOT NULL
          AND m.nextDate < :today
        ORDER BY m.nextDate ASC
        """)
    List<MaintenanceRecord> findOverdue(
        @org.springframework.data.repository.query.Param("today") java.time.LocalDate today
    );
}
