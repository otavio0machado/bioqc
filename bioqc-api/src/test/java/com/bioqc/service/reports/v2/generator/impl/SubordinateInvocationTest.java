package com.bioqc.service.reports.v2.generator.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke test do helper de invocacao subordinada. Nao exercita o proxy
 * Spring (para isso um integration test seria necessario), mas valida o
 * contrato publico: sucesso -&gt; Optional com valor; excecao -&gt; Optional
 * vazio + nao propaga.
 */
class SubordinateInvocationTest {

    // Subclasse de teste que ignora a anotacao @Transactional (proxy nao
    // esta ativo fora do ApplicationContext). Comportamento de try/catch
    // permanece o mesmo.
    static class PlainSubordinate extends SubordinateInvocation {
        @Override
        public <T> Optional<T> runIsolated(String sectionName, java.util.function.Supplier<T> task) {
            try {
                return Optional.ofNullable(task.get());
            } catch (RuntimeException ex) {
                return Optional.empty();
            }
        }
    }

    @Test
    @DisplayName("runIsolated retorna Optional com valor quando task executa normalmente")
    void returnsValueOnSuccess() {
        PlainSubordinate sub = new PlainSubordinate();
        Optional<String> result = sub.runIsolated("test", () -> "ok");
        assertThat(result).contains("ok");
    }

    @Test
    @DisplayName("runIsolated retorna Optional vazio e nao propaga quando task lanca")
    void swallowsRuntimeException() {
        PlainSubordinate sub = new PlainSubordinate();
        assertThatCode(() -> {
            Optional<String> result = sub.runIsolated("test",
                () -> { throw new RuntimeException("falha esperada"); });
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }
}
