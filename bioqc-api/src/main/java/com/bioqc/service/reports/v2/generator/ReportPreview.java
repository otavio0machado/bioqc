package com.bioqc.service.reports.v2.generator;

import java.util.List;

/**
 * Resultado de preview (HTML nao-persistido) de um relatorio V2.
 *
 * @param html        conteudo HTML renderizado; pode ser embutido diretamente no frontend
 * @param warnings    avisos (ex.: "nenhum registro", "periodo muito curto")
 * @param periodLabel rotulo do periodo aplicado
 */
public record ReportPreview(String html, List<String> warnings, String periodLabel) {
    public ReportPreview {
        if (html == null) html = "";
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
