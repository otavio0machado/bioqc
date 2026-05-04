package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bioqc.entity.QcRecord;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WestgardEngineTest {

    private final WestgardEngine engine = new WestgardEngine();

    @Test
    @DisplayName("deve retornar vazio quando o valor está na faixa normal")
    void shouldReturnEmptyWhenValueWithinNormalRange() {
        List<WestgardEngine.Violation> result = engine.evaluate(record(101, 100, 5), List.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deve detectar alerta 1-2s")
    void shouldDetect1_2sWarning() {
        List<WestgardEngine.Violation> result = engine.evaluate(record(111, 100, 5), List.of());
        assertThat(result).extracting(WestgardEngine.Violation::rule).contains("1-2s");
    }

    @Test
    @DisplayName("deve detectar rejeição 1-3s")
    void shouldDetect1_3sRejection() {
        List<WestgardEngine.Violation> result = engine.evaluate(record(116, 100, 5), List.of());
        assertThat(result).extracting(WestgardEngine.Violation::rule).contains("1-3s");
    }

    @Test
    @DisplayName("deve detectar 2-2s acima de 2DP")
    void shouldDetect2_2sWhenTwoConsecutiveAbove2SD() {
        List<WestgardEngine.Violation> result = engine.evaluate(
            record(111, 100, 5),
            List.of(record(112, 100, 5))
        );
        assertThat(result).extracting(WestgardEngine.Violation::rule).contains("2-2s");
    }

    @Test
    @DisplayName("deve detectar 2-2s abaixo de 2DP")
    void shouldDetect2_2sWhenTwoConsecutiveBelow2SD() {
        List<WestgardEngine.Violation> result = engine.evaluate(
            record(89, 100, 5),
            List.of(record(88, 100, 5))
        );
        assertThat(result).extracting(WestgardEngine.Violation::rule).contains("2-2s");
    }

    @Test
    @DisplayName("não deve detectar 2-2s em lados opostos")
    void shouldNotDetect2_2sWhenOnDifferentSides() {
        List<WestgardEngine.Violation> result = engine.evaluate(
            record(111, 100, 5),
            List.of(record(89, 100, 5))
        );
        assertThat(result).extracting(WestgardEngine.Violation::rule).doesNotContain("2-2s");
    }

    @Test
    @DisplayName("deve detectar R-4s quando a diferença passa de 4DP")
    void shouldDetectR4sWhenDifferenceExceeds4SD() {
        List<WestgardEngine.Violation> result = engine.evaluate(
            record(115, 100, 5),
            List.of(record(85, 100, 5))
        );
        assertThat(result).extracting(WestgardEngine.Violation::rule).contains("R-4s");
    }

    @Test
    @DisplayName("deve detectar 4-1s quando quatro pontos ficam acima de 1DP")
    void shouldDetect4_1sWhenFourConsecutiveAbove1SD() {
        List<WestgardEngine.Violation> result = engine.evaluate(
            record(106, 100, 5),
            List.of(record(107, 100, 5), record(108, 100, 5), record(109, 100, 5))
        );
        assertThat(result).extracting(WestgardEngine.Violation::rule).contains("4-1s");
    }

    @Test
    @DisplayName("deve detectar 10x quando dez pontos ficam do mesmo lado da média")
    void shouldDetect10xWhenTenConsecutiveSameSide() {
        List<QcRecord> history = List.of(
            record(101, 100, 5),
            record(102, 100, 5),
            record(103, 100, 5),
            record(104, 100, 5),
            record(105, 100, 5),
            record(101, 100, 5),
            record(102, 100, 5),
            record(103, 100, 5),
            record(104, 100, 5)
        );
        List<WestgardEngine.Violation> result = engine.evaluate(record(106, 100, 5), history);
        assertThat(result).extracting(WestgardEngine.Violation::rule).contains("10x");
    }

    @Test
    @DisplayName("deve retornar aviso quando o desvio padrão é zero")
    void shouldReturnSD0WarningWhenStandardDeviationIsZero() {
        List<WestgardEngine.Violation> result = engine.evaluate(record(100, 100, 0), List.of());
        assertThat(result).singleElement().extracting(WestgardEngine.Violation::rule).isEqualTo("SD=0");
    }

    @Test
    @DisplayName("deve retornar z-score zero quando o desvio padrão é zero")
    void shouldReturnZeroZScoreWhenStandardDeviationIsZero() {
        assertThat(engine.calculateZScore(record(100, 100, 0))).isEqualTo(0D);
    }

    @Test
    @DisplayName("deve lidar com histórico vazio")
    void shouldHandleEmptyHistory() {
        List<WestgardEngine.Violation> result = engine.evaluate(record(111, 100, 5), List.of());
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("deve lidar com histórico nulo")
    void shouldHandleNullHistory() {
        List<WestgardEngine.Violation> result = engine.evaluate(record(111, 100, 5), null);
        assertThat(result).isNotNull();
    }

    private QcRecord record(double value, double targetValue, double targetSd) {
        return QcRecord.builder()
            .value(value)
            .targetValue(targetValue)
            .targetSd(targetSd)
            .build();
    }
}
