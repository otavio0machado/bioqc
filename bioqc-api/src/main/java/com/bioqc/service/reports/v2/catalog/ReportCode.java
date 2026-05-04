package com.bioqc.service.reports.v2.catalog;

/**
 * Codigos estaveis de relatorios V2. Cada codigo e mapeado 1:1 para um
 * {@link ReportDefinition} no {@code ReportDefinitionRegistry} e, em runtime,
 * para um {@code ReportGenerator} via {@code ReportGeneratorRegistry}.
 */
public enum ReportCode {
    /** Relatorio operacional de CQ (F1 slice) — substitui funcionalmente o QC_PDF V1. */
    CQ_OPERATIONAL_V2,
    /** Analise profunda de regras Westgard com heatmap temporal. */
    WESTGARD_DEEPDIVE,
    /** Rastreabilidade de reagentes (lotes, vencimentos, movimentacoes). */
    REAGENTES_RASTREABILIDADE,
    /** KPIs de manutencao (MTBF, ratio preventiva/corretiva, atrasadas). */
    MANUTENCAO_KPI,
    /** Eficacia das calibracoes: CV antes/depois e improdutivas. */
    CALIBRACAO_PREPOST,
    /** Relatorio consolidado executivo multi-area. */
    MULTI_AREA_CONSOLIDADO,
    /** Pacote regulatorio (merge de todos os relatorios anteriores). */
    REGULATORIO_PACOTE
}
