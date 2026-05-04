package com.bioqc.service.reports.v2.generator.chart;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JFreeChartRendererTest {

    private final JFreeChartRenderer renderer = new JFreeChartRenderer();

    // PNG magic bytes: 89 50 4E 47
    private static final byte[] PNG_MAGIC = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 };

    @Test
    @DisplayName("renderLeveyJennings produz PNG valido")
    void renderLeveyJenningsReturnsPng() {
        List<ChartRenderer.LjPoint> pts = List.of(
            new ChartRenderer.LjPoint(LocalDate.of(2026, 4, 1), 100.0, "APROVADO"),
            new ChartRenderer.LjPoint(LocalDate.of(2026, 4, 2), 102.0, "APROVADO"),
            new ChartRenderer.LjPoint(LocalDate.of(2026, 4, 3), 105.0, "ALERTA"),
            new ChartRenderer.LjPoint(LocalDate.of(2026, 4, 4), 110.0, "REPROVADO")
        );
        byte[] png = renderer.renderLeveyJennings(pts, 100.0, 3.0, "Glicose N1");
        assertThat(png).isNotNull();
        assertThat(png.length).isGreaterThan(1024);
        assertPngMagic(png);
    }

    @Test
    @DisplayName("renderBarChart produz PNG valido")
    void renderBarChartReturnsPng() {
        Map<String, Number> ds = new LinkedHashMap<>();
        ds.put("Glicose", 5);
        ds.put("Ureia", 3);
        ds.put("Colesterol", 7);
        byte[] png = renderer.renderBarChart(ds, "Violacoes", "Exame", "N");
        assertPngMagic(png);
        assertThat(png.length).isGreaterThan(1024);
    }

    @Test
    @DisplayName("renderHeatmap produz PNG valido 7x5")
    void renderHeatmapReturnsPng() {
        double[][] matrix = new double[7][5];
        matrix[0][0] = 3; matrix[2][1] = 5; matrix[5][3] = 1;
        byte[] png = renderer.renderHeatmap(matrix,
            List.of("Seg", "Ter", "Qua", "Qui", "Sex", "Sab", "Dom"),
            List.of("Sem 1", "Sem 2", "Sem 3", "Sem 4", "Sem 5"),
            "Violacoes Westgard");
        assertPngMagic(png);
        assertThat(png.length).isGreaterThan(1024);
    }

    @Test
    @DisplayName("renderLeveyJennings aceita lista vazia sem excecao")
    void renderLeveyJenningsEmpty() {
        byte[] png = renderer.renderLeveyJennings(List.of(), 100.0, 3.0, "Vazio");
        assertPngMagic(png);
    }

    private void assertPngMagic(byte[] bytes) {
        assertThat(bytes.length).isGreaterThan(4);
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            assertThat(bytes[i]).isEqualTo(PNG_MAGIC[i]);
        }
    }
}
