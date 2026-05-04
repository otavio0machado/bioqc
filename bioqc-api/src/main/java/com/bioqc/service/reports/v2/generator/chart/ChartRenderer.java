package com.bioqc.service.reports.v2.generator.chart;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Contrato de renderizacao de graficos rasterizados (PNG) para Reports V2.
 *
 * <p>Todas as implementacoes devem retornar bytes PNG de tamanho fixo
 * 1200x600 @ 300 DPI e ser stateless/thread-safe. Cores e typografia
 * seguem o branding do laboratorio (verde escuro {@code #166534} como cor
 * principal, cores semanticas para status APROVADO/ALERTA/REPROVADO).
 */
public interface ChartRenderer {

    /**
     * Ponto individual de um grafico Levey-Jennings.
     *
     * @param date   data da medicao
     * @param value  valor medido
     * @param status status do ponto (APROVADO, ALERTA, REPROVADO); usado para colorir
     */
    record LjPoint(LocalDate date, double value, String status) {
        public LjPoint {
            if (date == null) {
                throw new IllegalArgumentException("LjPoint.date obrigatorio");
            }
            if (status == null || status.isBlank()) {
                status = "APROVADO";
            }
        }
    }

    /**
     * Renderiza um grafico de Levey-Jennings com linhas horizontais em
     * target, +/-1SD, +/-2SD, +/-3SD. Pontos sao coloridos por status.
     *
     * @param points lista de pontos; pode ser vazia (retorna PNG minimo)
     * @param target valor alvo (media esperada)
     * @param sd     desvio padrao
     * @param title  titulo exibido no topo
     * @return bytes PNG
     */
    byte[] renderLeveyJennings(List<LjPoint> points, double target, double sd, String title);

    /**
     * Renderiza um grafico de barras simples (categoria -> valor).
     *
     * @param dataset mapa ordenado de categoria -> valor. Use LinkedHashMap
     *                para garantir ordem de exibicao.
     * @param title   titulo do grafico
     * @param xLabel  rotulo do eixo X (categorias)
     * @param yLabel  rotulo do eixo Y (valor)
     * @return bytes PNG
     */
    byte[] renderBarChart(Map<String, Number> dataset, String title, String xLabel, String yLabel);

    /**
     * Renderiza um heatmap 2D. Usado para visualizar densidade de violacoes
     * Westgard por dia-da-semana (X) x semana-do-mes (Y).
     *
     * @param matrix       matriz de valores [colunas][linhas]; matrix[x][y]
     * @param xLabels      rotulos do eixo X (ex: "Seg", "Ter", ...)
     * @param yLabels      rotulos do eixo Y (ex: "Sem 1", "Sem 2", ...)
     * @param title        titulo do heatmap
     * @return bytes PNG
     */
    byte[] renderHeatmap(double[][] matrix, List<String> xLabels, List<String> yLabels, String title);
}
