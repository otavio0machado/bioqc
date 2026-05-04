package com.bioqc.service.reports.v2.generator;

import com.bioqc.service.reports.v2.catalog.ReportDefinition;

/**
 * Contrato basico de todo gerador V2. Cada implementacao e um Spring
 * {@code @Component} e contribui para o {@code ReportGeneratorRegistry}.
 */
public interface ReportGenerator {

    /**
     * Definicao estavel deste gerador (code, formatos, filtros, politicas).
     */
    ReportDefinition definition();

    /**
     * Gera o relatorio em formato final, persistente, numerado e auditado.
     *
     * @param filters filtros aplicados (ja validados pelo {@code FilterValidator})
     * @param ctx     contexto de execucao
     * @return artefato imutavel com bytes, hash e metadados
     */
    ReportArtifact generate(ReportFilters filters, GenerationContext ctx);

    /**
     * Gera preview HTML volatil — nao reserva numero, nao persiste no storage.
     * Deve ser idempotente e barato.
     */
    ReportPreview preview(ReportFilters filters, GenerationContext ctx);
}
