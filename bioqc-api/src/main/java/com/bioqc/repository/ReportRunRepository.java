package com.bioqc.repository;

import com.bioqc.entity.ReportRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRunRepository extends JpaRepository<ReportRun, UUID>, JpaSpecificationExecutor<ReportRun> {

    List<ReportRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<ReportRun> findByTypeOrderByCreatedAtDesc(String type, Pageable pageable);

    // ---------- Reports V2 ----------

    Optional<ReportRun> findByShareToken(String token);

    Page<ReportRun> findByReportCodeAndStatusOrderByCreatedAtDesc(String code, String status, Pageable pageable);

    /**
     * Busca execucoes cujo hash do PDF original ({@code sha256}) ou hash pos-assinatura
     * ({@code signatureHash}) bata com o valor informado. Usado pelo endpoint publico
     * {@code /verify/{hash}} para descobrir o numero do laudo a partir de qualquer
     * um dos dois hashes.
     */
    @Query("SELECT r FROM ReportRun r WHERE r.sha256 = :hash OR r.signatureHash = :hash")
    List<ReportRun> findBySha256OrSignatureHash(@Param("hash") String hash);

    /**
     * Query com filtros dinamicos nullables para o historico V2. Qualquer parametro
     * null e ignorado.
     */
    @Query("""
        SELECT r FROM ReportRun r
         WHERE (:code      IS NULL OR r.reportCode = :code)
           AND (:status    IS NULL OR r.status     = :status)
           AND (:username  IS NULL OR LOWER(r.username) = LOWER(:username))
           AND (:from      IS NULL OR r.createdAt >= :from)
           AND (:to        IS NULL OR r.createdAt <= :to)
         ORDER BY r.createdAt DESC
    """)
    Page<ReportRun> findByV2Filters(
        @Param("code") String code,
        @Param("status") String status,
        @Param("username") String username,
        @Param("from") Instant from,
        @Param("to") Instant to,
        Pageable pageable
    );
}
