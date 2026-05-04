package com.bioqc.service.reports.v2.catalog;

import java.util.Set;

/**
 * Descricao estatica de um relatorio V2 (metadados + spec de filtros + politicas).
 *
 * Exposto ao frontend via catalogo; consumido em runtime pelo
 * {@code ReportServiceV2} para resolver generator, validar filtros, decidir
 * retencao e checar permissoes.
 *
 * <p><strong>Sobre {@code retentionDays}:</strong> este campo controla apenas
 * o tempo minimo em que o <em>artefato</em> (PDF bruto) permanece acessivel
 * via {@code /api/reports/v2/executions/{id}/download}. Apos
 * {@code expiresAt = createdAt + retentionDays} o download pode ser bloqueado
 * ou o arquivo pode ser arquivado em storage frio.
 *
 * <p>O registro auditavel em {@code report_audit_log} e o log imutavel em
 * {@code report_signature_log} NAO sao afetados por {@code retentionDays} —
 * eles sao permanentes, independentemente da expiracao do artefato. Este
 * desenho cumpre RDC ANVISA 786/2023 + ISO 15189:2022, que exigem 5 anos de
 * retencao minima do laudo e guarda permanente do registro de responsabilidade.
 *
 * @param code                  codigo estavel (chave primaria)
 * @param name                  nome amigavel (pt-BR)
 * @param description           descricao curta
 * @param subtitle              subtitulo longo/tagline (pode ser null)
 * @param icon                  identificador de icone para UI (heroicons slug)
 * @param category              categoria de navegacao
 * @param supportedFormats      formatos que o generator sabe produzir
 * @param filterSpec            declaracao dos filtros aceitos
 * @param roleAccess            roles que podem gerar/visualizar
 * @param signatureRequired     true = relatorio exige assinatura explicita pos-geracao
 * @param previewSupported      true = suporta endpoint /preview (HTML)
 * @param aiCommentaryCapable   true = generator suporta injetar comentario IA
 * @param retentionDays         tempo <strong>minimo</strong> (em dias) para manter
 *                              o PDF acessivel via {@code /download}; nao afeta
 *                              auditoria ou log de assinatura
 * @param legalBasis            fundamento legal/regulatorio (texto livre, para rodape e auditoria)
 */
public record ReportDefinition(
    ReportCode code,
    String name,
    String description,
    String subtitle,
    String icon,
    ReportCategory category,
    Set<ReportFormat> supportedFormats,
    ReportFilterSpec filterSpec,
    Set<String> roleAccess,
    boolean signatureRequired,
    boolean previewSupported,
    boolean aiCommentaryCapable,
    int retentionDays,
    String legalBasis
) {
    public ReportDefinition {
        if (code == null) throw new IllegalArgumentException("ReportDefinition.code obrigatorio");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("ReportDefinition.name obrigatorio");
        if (category == null) throw new IllegalArgumentException("ReportDefinition.category obrigatorio");
        if (supportedFormats == null || supportedFormats.isEmpty()) {
            throw new IllegalArgumentException("ReportDefinition.supportedFormats nao pode ser vazio");
        }
        if (filterSpec == null) throw new IllegalArgumentException("ReportDefinition.filterSpec obrigatorio");
        if (roleAccess == null || roleAccess.isEmpty()) {
            throw new IllegalArgumentException("ReportDefinition.roleAccess nao pode ser vazio");
        }
        if (retentionDays < 1) {
            throw new IllegalArgumentException("ReportDefinition.retentionDays deve ser >= 1");
        }
        supportedFormats = Set.copyOf(supportedFormats);
        roleAccess = Set.copyOf(roleAccess);
    }
}
