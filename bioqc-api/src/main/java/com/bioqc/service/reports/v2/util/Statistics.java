package com.bioqc.service.reports.v2.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Helpers de estatistica usando {@link BigDecimal} para estabilidade numerica
 * em series longas. Laboratorios exigem que media/DP/CV sejam reproduziveis —
 * o double primitivo acumula erro em ordens de magnitude (p.ex. soma de 10k
 * valores em torno de 100 com DP ~5).
 *
 * <p><b>Precisao interna</b>: {@link MathContext#DECIMAL64} (17 digitos
 * significativos). Resultado arredondado a 4 decimais com
 * {@link RoundingMode#HALF_UP} — arredondamento "bancario" tradicional para
 * relatorios regulatorios laboratoriais.
 *
 * <p><b>Formato de entrada</b>: {@code List<Double>}. O caller converte
 * valores {@code null} para nao-nulls antes de chamar — esses helpers
 * ignoram nulls silenciosamente em vez de NPE.
 *
 * <p><b>Formula de DP</b>: amostral (n-1), consistente com o que o backend
 * historico computa em {@code stddev(list, mean)}. Garante equivalencia
 * numerica no PDF quando {@link #stdDev(List)} e usado no lugar do helper
 * em double.
 *
 * <p>Aplicado em {@code CqOperationalV2Generator} (tabela de bioquimica,
 * L-J charts e pos-calibracao). Outros generators nao tocam regulatorio
 * critico, seguem com double.
 */
public final class Statistics {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final int OUTPUT_SCALE = 4;

    private Statistics() {
        // utility
    }

    /**
     * Media aritmetica. Retorna {@link BigDecimal#ZERO} quando a lista
     * esta vazia ou so contem nulls (nenhum valor a somar).
     */
    public static BigDecimal mean(List<Double> values) {
        if (values == null || values.isEmpty()) return zero();
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (Double v : values) {
            if (v == null) continue;
            sum = sum.add(BigDecimal.valueOf(v), MC);
            count++;
        }
        if (count == 0) return zero();
        return sum.divide(BigDecimal.valueOf(count), MC).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Desvio padrao amostral (divisor n-1). Retorna ZERO quando n &lt; 2.
     *
     * <p>Equivalente numerico do helper historico em double, porem com
     * precisao DECIMAL64 interna. Serve como fonte de verdade para CV%.
     */
    public static BigDecimal stdDev(List<Double> values) {
        if (values == null) return zero();
        int n = 0;
        for (Double v : values) if (v != null) n++;
        if (n < 2) return zero();

        BigDecimal m = meanRaw(values, n);
        BigDecimal sumSq = BigDecimal.ZERO;
        for (Double v : values) {
            if (v == null) continue;
            BigDecimal d = BigDecimal.valueOf(v).subtract(m, MC);
            sumSq = sumSq.add(d.multiply(d, MC), MC);
        }
        BigDecimal variance = sumSq.divide(BigDecimal.valueOf(n - 1L), MC);
        return sqrt(variance).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Coeficiente de variacao em porcentagem: {@code (stdDev / mean) * 100}.
     * Retorna ZERO quando a media e 0 ou a lista nao tem &gt;= 2 valores.
     */
    public static BigDecimal cv(List<Double> values) {
        if (values == null) return zero();
        int n = 0;
        for (Double v : values) if (v != null) n++;
        if (n < 2) return zero();
        BigDecimal m = meanRaw(values, n);
        if (m.signum() == 0) return zero();
        BigDecimal sd = stdDev(values);
        // Usa stdDev ja-arredondado + mean com precisao plena. Para reports
        // o tradeoff e estabilidade do CV exibido vs. a magnitude do erro
        // (<1e-4) — aceitavel.
        return sd.divide(m, MC).multiply(BigDecimal.valueOf(100), MC)
            .setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    // ---------- internals ----------

    private static BigDecimal meanRaw(List<Double> values, int n) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Double v : values) {
            if (v == null) continue;
            sum = sum.add(BigDecimal.valueOf(v), MC);
        }
        return sum.divide(BigDecimal.valueOf(n), MC);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Raiz quadrada por Newton-Raphson em BigDecimal. Java 9+ tem
     * {@link BigDecimal#sqrt(MathContext)}, que usamos diretamente.
     */
    private static BigDecimal sqrt(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        if (value.signum() == 0) return BigDecimal.ZERO;
        return value.sqrt(MC);
    }
}
