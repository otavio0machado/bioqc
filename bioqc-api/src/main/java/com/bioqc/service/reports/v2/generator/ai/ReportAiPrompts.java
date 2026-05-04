package com.bioqc.service.reports.v2.generator.ai;

import com.bioqc.service.reports.v2.catalog.ReportCode;
import org.springframework.stereotype.Component;

/**
 * Prompts de IA por tipo de relatorio. Cada prompt:
 * <ul>
 *   <li>Inicia definindo o papel do modelo (analista laboratorial experiente)</li>
 *   <li>Explica o foco da analise</li>
 *   <li>Termina pedindo 3-5 frases em pt-BR com uma recomendacao de acao</li>
 * </ul>
 *
 * <p>O {@code structuredContext} e concatenado apos este prompt pelo
 * {@code ReportAiCommentator}, entregando os dados brutos do periodo.
 * Domain-auditor valida estes prompts em fase posterior.
 */
@Component
public class ReportAiPrompts {

    private static final String ROLE_PREFIX =
        "Voce e um analista laboratorial experiente com especializacao em controle de qualidade "
        + "em laboratorios clinicos brasileiros. Siga a RDC ANVISA 786/2023 e ISO 15189:2022. ";

    private static final String CLOSING =
        "\n\nForneca sua analise em portugues do Brasil, em 3 a 5 frases diretas, "
        + "terminando com uma recomendacao de acao objetiva e acionavel. "
        + "Evite jargao excessivo. Nao invente numeros que nao estejam no contexto.";

    public String promptFor(ReportCode code) {
        if (code == null) {
            return ROLE_PREFIX + "Analise o relatorio laboratorial abaixo." + CLOSING;
        }
        return switch (code) {
            case CQ_OPERATIONAL_V2 -> ROLE_PREFIX
                + "Analise o relatorio operacional de Controle de Qualidade. Concentre-se em: "
                + "(1) taxa de aprovacao do periodo comparada com o periodo anterior; "
                + "(2) exames ou lotes com CV elevado (>75% do limite) que precisam atencao; "
                + "(3) eficacia de eventuais pos-calibracoes realizadas. "
                + "Aponte tendencias de deriva e recomende se algum processo exige revisao imediata."
                + CLOSING;
            case WESTGARD_DEEPDIVE -> ROLE_PREFIX
                + "Analise as violacoes Westgard do periodo. Concentre-se em: "
                + "(1) quais regras foram mais violadas (1-3s, 2-2s, R-4s, 4-1s, 10x); "
                + "(2) exames com maior taxa de rejeicao; "
                + "(3) padroes temporais (concentracoes em dias da semana ou semanas especificas). "
                + "Diferencie alertas (advertencia) de rejeicoes (erro sistematico) e proponha investigacao direcionada."
                + CLOSING;
            case REAGENTES_RASTREABILIDADE -> ROLE_PREFIX
                + "Analise o panorama de rastreabilidade de reagentes. Concentre-se em: "
                + "(1) lotes com vencimento proximo (30/60/90 dias); "
                + "(2) lotes vencidos com estoque remanescente (risco regulatorio); "
                + "(3) consumo por categoria e lotes ociosos sem movimentacao. "
                + "Priorize risco de descarte e indique quais lotes devem ser usados primeiro."
                + CLOSING;
            case MANUTENCAO_KPI -> ROLE_PREFIX
                + "Analise os KPIs de manutencao de equipamentos. Concentre-se em: "
                + "(1) MTBF (tempo medio entre manutencoes) por equipamento; "
                + "(2) razao entre manutencoes preventivas e corretivas; "
                + "(3) manutencoes atrasadas (nextDate < hoje) e proximas no horizonte 30/60/90 dias. "
                + "Aponte equipamentos com manutencao corretiva excessiva que podem exigir substituicao."
                + CLOSING;
            case CALIBRACAO_PREPOST -> ROLE_PREFIX
                + "Analise a eficacia das calibracoes realizadas no periodo. Concentre-se em: "
                + "(1) delta de CV antes vs depois (calibracao eficaz = delta negativo); "
                + "(2) calibracoes sem efeito ou que pioraram o CV (investigar); "
                + "(3) exames ou equipamentos que repetem calibracao sem convergir. "
                + "Recomende acoes para as calibracoes improdutivas."
                + CLOSING;
            case MULTI_AREA_CONSOLIDADO -> ROLE_PREFIX
                + "Analise o relatorio consolidado executivo do laboratorio, cruzando todas as areas. "
                + "Concentre-se em: "
                + "(1) areas com pior taxa de aprovacao de CQ; "
                + "(2) alertas transversais (violacoes Westgard de rejeicao, reagentes vencidos, manutencoes atrasadas); "
                + "(3) visao gerencial do risco operacional do mes. "
                + "Forneca uma recomendacao gerencial de alto nivel."
                + CLOSING;
            case REGULATORIO_PACOTE -> ROLE_PREFIX
                + "Voce esta revisando um pacote regulatorio consolidado para vigilancia sanitaria. "
                + "Avalie se o conjunto documenta adequadamente o controle laboratorial e indique "
                + "pontos que podem gerar nao-conformidade em auditoria."
                + CLOSING;
        };
    }
}
