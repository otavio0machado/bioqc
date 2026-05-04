package com.bioqc.service.reports.v2.catalog;

import java.util.List;

/**
 * Descricao de um campo de filtro aceito por um {@link ReportDefinition}.
 *
 * @param key          identificador estavel (usado como chave no mapa de filtros enviado pelo cliente)
 * @param type         tipo esperado do valor
 * @param required     se o campo e obrigatorio para disparar a geracao
 * @param allowedValues lista de valores validos (usado para {@link ReportFilterFieldType#STRING_ENUM});
 *                      ignorado para outros tipos. Pode ser {@code null}.
 * @param label        rotulo amigavel para UI (pt-BR)
 * @param helpText     texto auxiliar para UI (pt-BR); pode ser {@code null}
 */
public record ReportFilterField(
    String key,
    ReportFilterFieldType type,
    boolean required,
    List<String> allowedValues,
    String label,
    String helpText
) {
    public ReportFilterField {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("ReportFilterField.key nao pode ser vazio");
        }
        if (type == null) {
            throw new IllegalArgumentException("ReportFilterField.type nao pode ser null");
        }
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }
}
