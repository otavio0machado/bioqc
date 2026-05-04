package com.bioqc.repository;

import com.bioqc.entity.ReagentLot;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReagentLotRepository extends JpaRepository<ReagentLot, UUID> {

    /**
     * Projection do agregado por etiqueta. A coluna do banco continua sendo {@code name}
     * (vide refator-reagentes-v2 §1.1). O getter {@link #getLabel()} expoe o valor sob
     * a semantica nova.
     *
     * <p>Refator v3: alias {@code getName()} removido junto com {@code ReagentTagSummary}
     * (decisao 1.10). Campo {@code inativos} substitui {@code foraDeEstoque}.</p>
     */
    interface ReagentLabelSummaryProjection {
        String getLabel();
        long getTotal();
        long getEmEstoque();
        long getEmUso();
        long getInativos();
        long getVencidos();
    }

    List<ReagentLot> findAllByOrderByCreatedAtDesc();

    List<ReagentLot> findByCategory(String category);

    List<ReagentLot> findByStatus(String status);

    @Query("SELECT r FROM ReagentLot r WHERE (:category IS NULL OR r.category = :category) AND (:status IS NULL OR r.status = :status) ORDER BY r.createdAt DESC")
    List<ReagentLot> findByFilters(@Param("category") String category, @Param("status") String status);

    List<ReagentLot> findByLotNumberIgnoreCase(String lotNumber);

    /**
     * Usado em updateLot para garantir que a mudanca de (lotNumber, manufacturer)
     * nao colida com outro lote ja existente.
     */
    @Query("""
        SELECT r FROM ReagentLot r
        WHERE LOWER(r.lotNumber) = LOWER(:lotNumber)
          AND LOWER(COALESCE(r.manufacturer, '')) = LOWER(COALESCE(:manufacturer, ''))
        """)
    List<ReagentLot> findByLotNumberAndManufacturer(
        @Param("lotNumber") String lotNumber,
        @Param("manufacturer") String manufacturer);

    /**
     * Retorna lotes cuja validade ja passou e cujo status ainda nao foi reclassificado.
     * v3: scheduler NAO toca lote {@code inativo} (terminal manual — decisao 1.1 do contrato).
     */
    @Query("""
        SELECT r FROM ReagentLot r
        WHERE r.expiryDate < :today
          AND r.status NOT IN ('vencido', 'inativo')
        """)
    List<ReagentLot> findExpiredNeedingReclassification(@Param("today") LocalDate today);

    @Query("""
        SELECT r FROM ReagentLot r
        WHERE r.expiryDate BETWEEN :startDate AND :endDate
          AND r.status NOT IN ('vencido', 'inativo')
        ORDER BY r.expiryDate ASC
        """)
    List<ReagentLot> findExpiringLots(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Janela ampla para reports V2 (inclui vencidos para tabelas de auditoria).
     * Ordena por data de expiry para tabelas no PDF.
     */
    @Query("""
        SELECT r FROM ReagentLot r
        WHERE r.expiryDate BETWEEN :s AND :e
        ORDER BY r.expiryDate ASC
        """)
    List<ReagentLot> findExpiringInWindow(@Param("s") LocalDate s, @Param("e") LocalDate e);

    /**
     * Lotes ja vencidos que ainda possuem estoque (qualquer unidade fechada ou aberta).
     * Refator v3: troca {@code currentStock > 0} por
     * {@code (unitsInStock + unitsInUse) > 0}.
     */
    @Query("""
        SELECT r FROM ReagentLot r
        WHERE r.status = 'vencido'
          AND (COALESCE(r.unitsInStock, 0) + COALESCE(r.unitsInUse, 0)) > 0
        ORDER BY r.expiryDate ASC
        """)
    List<ReagentLot> findExpiredWithStock();

    /**
     * Contagem rapida de lotes vencidos com estoque (sem carregar entidades).
     */
    @Query("""
        SELECT COUNT(r) FROM ReagentLot r
        WHERE r.expiryDate IS NOT NULL
          AND r.expiryDate < :today
          AND (COALESCE(r.unitsInStock, 0) + COALESCE(r.unitsInUse, 0)) > 0
        """)
    long countExpiredWithStock(@Param("today") LocalDate today);

    @Query("""
        SELECT COUNT(r) FROM ReagentLot r
        WHERE r.expiryDate BETWEEN :startDate AND :endDate
          AND r.status NOT IN ('vencido', 'inativo')
        """)
    long countExpiringLots(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Agrega lotes por etiqueta com contagens por status novo (refator v3 — campo
     * {@code inativos} substitui {@code foraDeEstoque}).
     */
    @Query("""
        SELECT
          r.name AS label,
          COUNT(r) AS total,
          SUM(CASE WHEN r.status = 'em_estoque' THEN 1 ELSE 0 END) AS emEstoque,
          SUM(CASE WHEN r.status = 'em_uso' THEN 1 ELSE 0 END) AS emUso,
          SUM(CASE WHEN r.status = 'inativo' THEN 1 ELSE 0 END) AS inativos,
          SUM(CASE WHEN r.status = 'vencido' THEN 1 ELSE 0 END) AS vencidos
        FROM ReagentLot r
        GROUP BY r.name
        ORDER BY r.name
        """)
    List<ReagentLabelSummaryProjection> findLabelSummaries();
}
