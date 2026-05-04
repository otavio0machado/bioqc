package com.bioqc.repository;

import com.bioqc.entity.ReportSignatureLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio append-only para {@link ReportSignatureLog}. Os fluxos de negocio
 * devem usar exclusivamente os metodos declarados aqui — nao utilize
 * {@code save(iterable)}, {@code deleteBy*} ou outros herdados da interface
 * padrao que violem a natureza imutavel do log. (JpaRepository e mantido como
 * superinterface apenas pela integracao com Spring Data; convencao de codigo.)
 */
public interface ReportSignatureLogRepository extends JpaRepository<ReportSignatureLog, UUID> {

    /**
     * Retorna todas as linhas para uma execucao especifica, ordenadas pela
     * data de assinatura descendente. Em teoria, cada {@code ReportRun} so
     * pode ser assinado uma vez (protegido no service por
     * {@code ReportAlreadySignedException}), mas o retorno e {@code List}
     * para permitir auditoria caso algum fluxo futuro permita reassinatura.
     */
    List<ReportSignatureLog> findByReportRunIdOrderBySignedAtDesc(UUID reportRunId);

    /**
     * Busca um registro pelo hash pos-assinatura. Usado pelo endpoint publico
     * {@code /verify/{hash}} quando o cliente fornece o hash da versao assinada.
     */
    Optional<ReportSignatureLog> findBySignatureHash(String signatureHash);

    /**
     * Busca por hash original (pre-assinatura). Pode retornar multiplas linhas
     * se o fluxo de reassinatura for habilitado no futuro.
     */
    List<ReportSignatureLog> findByOriginalSha256OrderBySignedAtDesc(String originalSha256);
}
