package com.bioqc.service.reports.v2.generator.ai;

import com.bioqc.service.reports.v2.catalog.ReportCode;
import com.bioqc.service.reports.v2.generator.GenerationContext;

/**
 * Gera comentario textual executivo para cada relatorio V2 com base em
 * contexto estruturado. A implementacao padrao delega para o
 * {@code GeminiAiService} com timeout hard de 15s e fallback seguro.
 *
 * <p><strong>Garantias:</strong> nunca lanca excecao — qualquer falha
 * retorna a string fallback {@code FALLBACK_COMMENTARY} para que o relatorio
 * seja emitido mesmo com IA indisponivel. Relatorios sao validos apenas
 * pelos dados numericos, nao pelo comentario.
 */
public interface ReportAiCommentator {

    /**
     * Mensagem padrao quando a IA nao esta disponivel. Laboratoristas e
     * auditores devem ler os dados numericos — a analise textual e um plus.
     */
    String FALLBACK_COMMENTARY =
        "Analise automatica indisponivel no momento. Relatorio valido apenas pelos dados numericos acima.";

    /**
     * Gera comentario de 3-5 frases em pt-BR para o relatorio identificado
     * por {@code code}, usando {@code structuredContext} como insumo.
     *
     * @param code              tipo do relatorio (seleciona prompt apropriado)
     * @param structuredContext resumo textual dos dados (tabelas em linguagem natural)
     * @param ctx               contexto da geracao (usuario, periodo, lab)
     * @return comentario executivo ou {@link #FALLBACK_COMMENTARY} em caso de falha
     */
    String commentary(ReportCode code, String structuredContext, GenerationContext ctx);
}
