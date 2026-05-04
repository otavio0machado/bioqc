package com.bioqc.service.reports.v2.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatisticsTest {

    @Test
    @DisplayName("mean de lista simples")
    void meanBasic() {
        assertThat(Statistics.mean(List.of(1.0, 2.0, 3.0, 4.0, 5.0)))
            .isEqualByComparingTo(new BigDecimal("3.0000"));
    }

    @Test
    @DisplayName("mean de lista vazia retorna zero")
    void meanEmpty() {
        assertThat(Statistics.mean(List.of())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("mean ignora nulls")
    void meanIgnoresNulls() {
        assertThat(Statistics.mean(Arrays.asList(2.0, null, 4.0)))
            .isEqualByComparingTo(new BigDecimal("3.0000"));
    }

    @Test
    @DisplayName("stdDev amostral (n-1) equivale a helper historico double")
    void stdDevSampleMatchesDouble() {
        List<Double> values = List.of(100.0, 102.0, 98.0, 101.0, 99.0);
        double expected = Math.sqrt(((0.0 + 4.0 + 4.0 + 1.0 + 1.0) / 4.0));
        BigDecimal actual = Statistics.stdDev(values);
        assertThat(actual.doubleValue()).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-4));
    }

    @Test
    @DisplayName("stdDev com n < 2 retorna zero")
    void stdDevSmallSample() {
        assertThat(Statistics.stdDev(List.of(10.0))).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(Statistics.stdDev(List.of())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("cv = stdDev/mean * 100")
    void cvBasic() {
        // Target 100, SD ~1.58 -> CV ~1.58%
        List<Double> values = List.of(100.0, 102.0, 98.0, 101.0, 99.0);
        BigDecimal cv = Statistics.cv(values);
        assertThat(cv.doubleValue()).isCloseTo(1.5811, org.assertj.core.data.Offset.offset(1e-3));
    }

    @Test
    @DisplayName("cv retorna zero se mean=0")
    void cvWithZeroMean() {
        List<Double> values = List.of(0.0, 0.0, 0.0, 0.0);
        assertThat(Statistics.cv(values)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("mean estavel em serie longa de mesmo valor (imune ao drift de double)")
    void meanStableOnLongSeries() {
        // 10000 pontos identicos — double cumulativo pode drift, BigDecimal nao
        Double[] vals = new Double[10_000];
        Arrays.fill(vals, 0.1);
        BigDecimal m = Statistics.mean(List.of(vals));
        assertThat(m.doubleValue()).isCloseTo(0.1, org.assertj.core.data.Offset.offset(1e-9));
    }
}
