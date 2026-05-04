package com.bioqc.service.reports.v2.generator.chart;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.SymbolAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.DefaultXYZDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementacao {@link ChartRenderer} baseada em JFreeChart. Stateless,
 * thread-safe e deterministico. Todos os graficos sao renderizados em
 * 1200x600 pixels @ 300 DPI (via {@link ChartUtils#writeBufferedImageAsPNG})
 * para impressao de alta qualidade em PDFs de relatorios regulatorios.
 */
@Component
public class JFreeChartRenderer implements ChartRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(JFreeChartRenderer.class);

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 600;
    /** DPI alto (300) para impressao em laudos regulatorios. */
    private static final int DPI = 300;

    // Paleta institucional (verde escuro #166534)
    private static final Color BRAND_PRIMARY = new Color(22, 101, 52);
    private static final Color BRAND_SECONDARY = new Color(20, 83, 45);
    private static final Color BRAND_LIGHT = new Color(187, 247, 208);

    // Cores semanticas para status
    private static final Color STATUS_APROVADO = new Color(22, 163, 74);
    private static final Color STATUS_ALERTA = new Color(234, 179, 8);
    private static final Color STATUS_REPROVADO = new Color(220, 38, 38);

    private static final Color BACKGROUND = Color.WHITE;
    private static final Color GRID_COLOR = new Color(229, 231, 235);
    private static final Color AXIS_COLOR = new Color(75, 85, 99);

    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 16);
    private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 11);
    private static final Font TICK_FONT = new Font("SansSerif", Font.PLAIN, 10);

    @Override
    public byte[] renderLeveyJennings(List<LjPoint> points, double target, double sd, String title) {
        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries series = new XYSeries("Medicoes");

        List<Paint> pointColors = new ArrayList<>();
        if (points != null) {
            for (LjPoint p : points) {
                long x = p.date().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                series.add(x, p.value());
                pointColors.add(colorForStatus(p.status()));
            }
        }
        dataset.addSeries(series);

        NumberAxis xAxis = new NumberAxis("Data");
        xAxis.setAutoRangeIncludesZero(false);
        xAxis.setNumberFormatOverride(new java.text.DecimalFormat() {
            @Override
            public StringBuffer format(double number, StringBuffer toAppendTo, java.text.FieldPosition pos) {
                LocalDate d = java.time.Instant.ofEpochMilli((long) number)
                    .atZone(ZoneId.systemDefault()).toLocalDate();
                toAppendTo.append(d.getDayOfMonth()).append("/").append(d.getMonthValue());
                return toAppendTo;
            }
        });
        xAxis.setTickLabelFont(TICK_FONT);
        xAxis.setLabelFont(LABEL_FONT);
        xAxis.setAxisLinePaint(AXIS_COLOR);
        xAxis.setTickLabelPaint(AXIS_COLOR);

        NumberAxis yAxis = new NumberAxis("Valor");
        yAxis.setAutoRangeIncludesZero(false);
        yAxis.setTickLabelFont(TICK_FONT);
        yAxis.setLabelFont(LABEL_FONT);
        yAxis.setAxisLinePaint(AXIS_COLOR);
        yAxis.setTickLabelPaint(AXIS_COLOR);

        final List<Paint> finalColors = pointColors;
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true) {
            @Override
            public Paint getItemPaint(int row, int col) {
                if (col < finalColors.size()) {
                    return finalColors.get(col);
                }
                return STATUS_APROVADO;
            }

            @Override
            public Shape getItemShape(int row, int col) {
                return new Ellipse2D.Double(-4.0, -4.0, 8.0, 8.0);
            }
        };
        renderer.setSeriesStroke(0, new BasicStroke(1.2f));
        renderer.setSeriesPaint(0, new Color(75, 85, 99, 180));
        renderer.setDefaultShapesFilled(true);

        XYPlot plot = new XYPlot(dataset, xAxis, yAxis, renderer);
        plot.setBackgroundPaint(BACKGROUND);
        plot.setDomainGridlinePaint(GRID_COLOR);
        plot.setRangeGridlinePaint(GRID_COLOR);
        plot.setOutlineVisible(false);

        // Linhas de referencia: target, +/-1SD, +/-2SD, +/-3SD (pontilhadas)
        addSdMarker(plot, target, new Color(30, 41, 59), 1.4f, false, "Media");
        addSdMarker(plot, target + sd, new Color(100, 116, 139), 0.9f, true, "+1SD");
        addSdMarker(plot, target - sd, new Color(100, 116, 139), 0.9f, true, "-1SD");
        addSdMarker(plot, target + 2 * sd, new Color(234, 179, 8), 0.9f, true, "+2SD");
        addSdMarker(plot, target - 2 * sd, new Color(234, 179, 8), 0.9f, true, "-2SD");
        addSdMarker(plot, target + 3 * sd, new Color(220, 38, 38), 0.9f, true, "+3SD");
        addSdMarker(plot, target - 3 * sd, new Color(220, 38, 38), 0.9f, true, "-3SD");

        JFreeChart chart = new JFreeChart(title, TITLE_FONT, plot, false);
        chart.setBackgroundPaint(BACKGROUND);
        TextTitle text = chart.getTitle();
        if (text != null) {
            text.setPaint(BRAND_SECONDARY);
            text.setPadding(new RectangleInsets(4, 0, 8, 0));
        }

        return toPngBytes(chart);
    }

    @Override
    public byte[] renderBarChart(Map<String, Number> dataset, String title, String xLabel, String yLabel) {
        DefaultCategoryDataset data = new DefaultCategoryDataset();
        if (dataset != null) {
            for (Map.Entry<String, Number> e : dataset.entrySet()) {
                Number v = e.getValue() == null ? 0 : e.getValue();
                data.addValue(v, "valor", e.getKey());
            }
        }

        CategoryAxis domainAxis = new CategoryAxis(xLabel);
        domainAxis.setTickLabelFont(TICK_FONT);
        domainAxis.setLabelFont(LABEL_FONT);
        domainAxis.setTickLabelPaint(AXIS_COLOR);
        domainAxis.setAxisLinePaint(AXIS_COLOR);
        domainAxis.setCategoryLabelPositions(
            org.jfree.chart.axis.CategoryLabelPositions.UP_45);

        NumberAxis rangeAxis = new NumberAxis(yLabel);
        rangeAxis.setTickLabelFont(TICK_FONT);
        rangeAxis.setLabelFont(LABEL_FONT);
        rangeAxis.setTickLabelPaint(AXIS_COLOR);
        rangeAxis.setAxisLinePaint(AXIS_COLOR);

        // BarRenderer com cor alternada branding
        final Color[] palette = new Color[] { BRAND_PRIMARY, BRAND_SECONDARY };
        BarRenderer renderer = new BarRenderer() {
            @Override
            public Paint getItemPaint(int row, int col) {
                return palette[col % palette.length];
            }
        };
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);
        renderer.setBarPainter(new org.jfree.chart.renderer.category.StandardBarPainter());

        CategoryPlot plot = new CategoryPlot(data, domainAxis, rangeAxis, renderer);
        plot.setBackgroundPaint(BACKGROUND);
        plot.setRangeGridlinePaint(GRID_COLOR);
        plot.setOutlineVisible(false);

        JFreeChart chart = new JFreeChart(title, TITLE_FONT, plot, false);
        chart.setBackgroundPaint(BACKGROUND);
        TextTitle text = chart.getTitle();
        if (text != null) {
            text.setPaint(BRAND_SECONDARY);
            text.setPadding(new RectangleInsets(4, 0, 8, 0));
        }
        return toPngBytes(chart);
    }

    @Override
    public byte[] renderHeatmap(double[][] matrix, List<String> xLabels, List<String> yLabels, String title) {
        int cols = (matrix == null) ? 0 : matrix.length;
        int rows = (cols == 0 || matrix[0] == null) ? 0 : matrix[0].length;

        DefaultXYZDataset dataset = new DefaultXYZDataset();
        int total = Math.max(1, cols * rows);
        double[] xs = new double[total];
        double[] ys = new double[total];
        double[] zs = new double[total];
        double max = 0.0;
        int idx = 0;
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                double v = matrix[x][y];
                xs[idx] = x;
                ys[idx] = y;
                zs[idx] = v;
                if (v > max) max = v;
                idx++;
            }
        }
        // Preenche dimensoes restantes com zero caso matrix seja menor
        while (idx < total) {
            xs[idx] = 0; ys[idx] = 0; zs[idx] = 0;
            idx++;
        }
        dataset.addSeries("violacoes", new double[][] { xs, ys, zs });

        SymbolAxis xAxis = new SymbolAxis(null,
            (xLabels == null ? new String[0] : xLabels.toArray(new String[0])));
        xAxis.setTickLabelFont(TICK_FONT);
        xAxis.setTickLabelPaint(AXIS_COLOR);
        xAxis.setAxisLinePaint(AXIS_COLOR);
        xAxis.setGridBandsVisible(false);

        SymbolAxis yAxis = new SymbolAxis(null,
            (yLabels == null ? new String[0] : yLabels.toArray(new String[0])));
        yAxis.setTickLabelFont(TICK_FONT);
        yAxis.setTickLabelPaint(AXIS_COLOR);
        yAxis.setAxisLinePaint(AXIS_COLOR);
        yAxis.setGridBandsVisible(false);

        XYBlockRenderer renderer = new XYBlockRenderer();
        renderer.setBlockAnchor(RectangleAnchor.CENTER);
        // Paleta de azuis: claro (0) -> escuro (max)
        LookupPaintScale scale = new LookupPaintScale(0.0, Math.max(max, 1.0) + 0.0001,
            new Color(226, 232, 240));
        double upper = Math.max(max, 1.0);
        scale.add(0.0, new Color(226, 232, 240));
        scale.add(upper * 0.2, new Color(191, 219, 254));
        scale.add(upper * 0.4, new Color(147, 197, 253));
        scale.add(upper * 0.6, new Color(96, 165, 250));
        scale.add(upper * 0.8, new Color(59, 130, 246));
        scale.add(upper, new Color(30, 64, 175));
        renderer.setPaintScale(scale);

        XYPlot plot = new XYPlot(dataset, xAxis, yAxis, renderer);
        plot.setBackgroundPaint(BACKGROUND);
        plot.setDomainGridlinePaint(GRID_COLOR);
        plot.setRangeGridlinePaint(GRID_COLOR);
        plot.setOutlineVisible(false);
        plot.setOrientation(PlotOrientation.VERTICAL);

        JFreeChart chart = new JFreeChart(title, TITLE_FONT, plot, false);
        chart.setBackgroundPaint(BACKGROUND);
        TextTitle text = chart.getTitle();
        if (text != null) {
            text.setPaint(BRAND_SECONDARY);
            text.setPadding(new RectangleInsets(4, 0, 8, 0));
        }
        return toPngBytes(chart);
    }

    // ---------- Helpers ----------

    private void addSdMarker(XYPlot plot, double value, Color color, float stroke, boolean dashed, String label) {
        ValueMarker marker = new ValueMarker(value);
        marker.setPaint(color);
        if (dashed) {
            marker.setStroke(new BasicStroke(
                stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f,
                new float[] { 4.0f, 3.0f }, 0.0f));
        } else {
            marker.setStroke(new BasicStroke(stroke));
        }
        marker.setLabelFont(TICK_FONT);
        marker.setLabelPaint(color);
        marker.setLabel(label);
        marker.setLabelAnchor(RectangleAnchor.RIGHT);
        plot.addRangeMarker(marker);
    }

    private Color colorForStatus(String status) {
        if (status == null) return STATUS_APROVADO;
        return switch (status.toUpperCase()) {
            case "REPROVADO" -> STATUS_REPROVADO;
            case "ALERTA" -> STATUS_ALERTA;
            default -> STATUS_APROVADO;
        };
    }

    private byte[] toPngBytes(JFreeChart chart) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage image = chart.createBufferedImage(WIDTH, HEIGHT);
            ChartUtils.writeBufferedImageAsPNG(out, image);
            return out.toByteArray();
        } catch (IOException ex) {
            LOG.error("Falha ao renderizar chart PNG", ex);
            throw new IllegalStateException("Falha ao renderizar chart PNG", ex);
        }
    }

    /** Exposta para debug/documentacao. */
    public int widthPixels() { return WIDTH; }
    /** Exposta para debug/documentacao. */
    public int heightPixels() { return HEIGHT; }
    /** Exposta para debug/documentacao. */
    public int dpi() { return DPI; }
}
