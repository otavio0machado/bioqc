package com.bioqc.service.reports.v2.generator.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.bioqc.exception.BusinessException;
import com.bioqc.service.GeminiAiService;
import com.bioqc.service.reports.v2.catalog.ReportCode;
import com.bioqc.service.reports.v2.generator.GenerationContext;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultReportAiCommentatorTest {

    private final ReportAiPrompts prompts = new ReportAiPrompts();
    private final GenerationContext ctx = new GenerationContext(
        UUID.randomUUID(), "tester", Set.of("ADMIN"), Instant.now(),
        ZoneId.of("America/Sao_Paulo"), null, "corr", "req");

    @Test
    @DisplayName("commentary retorna o texto quando Gemini responde normalmente")
    void commentaryReturnsTextOnSuccess() {
        GeminiAiService gemini = stubAnalyze((p, c) -> "Comentario valido de 3 frases.");
        DefaultReportAiCommentator commentator = new DefaultReportAiCommentator(
            gemini, prompts, Executors.newSingleThreadExecutor(), Duration.ofSeconds(5));
        String result = commentator.commentary(ReportCode.CQ_OPERATIONAL_V2, "ctx", ctx);
        assertThat(result).isEqualTo("Comentario valido de 3 frases.");
    }

    @Test
    @DisplayName("commentary retorna fallback quando Gemini retorna 'Nao foi possivel analisar'")
    void commentaryFallbackOnFriendlyError() {
        GeminiAiService gemini = stubAnalyze((p, c) -> "Não foi possível analisar no momento. Tente novamente.");
        DefaultReportAiCommentator commentator = new DefaultReportAiCommentator(
            gemini, prompts, Executors.newSingleThreadExecutor(), Duration.ofSeconds(5));
        String result = commentator.commentary(ReportCode.CQ_OPERATIONAL_V2, "ctx", ctx);
        assertThat(result).isEqualTo(ReportAiCommentator.FALLBACK_COMMENTARY);
    }

    @Test
    @DisplayName("commentary retorna fallback quando Gemini lanca excecao")
    void commentaryFallbackOnException() {
        GeminiAiService gemini = stubAnalyze((p, c) -> {
            throw new RuntimeException("Gemini down");
        });
        DefaultReportAiCommentator commentator = new DefaultReportAiCommentator(
            gemini, prompts, Executors.newSingleThreadExecutor(), Duration.ofSeconds(5));
        String result = commentator.commentary(ReportCode.WESTGARD_DEEPDIVE, "ctx", ctx);
        assertThat(result).isEqualTo(ReportAiCommentator.FALLBACK_COMMENTARY);
    }

    @Test
    @DisplayName("commentary retorna fallback em timeout")
    void commentaryFallbackOnTimeout() {
        GeminiAiService gemini = stubAnalyze((p, c) -> {
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return "nunca chega";
        });
        DefaultReportAiCommentator commentator = new DefaultReportAiCommentator(
            gemini, prompts, Executors.newSingleThreadExecutor(), Duration.ofMillis(100));
        String result = commentator.commentary(ReportCode.MANUTENCAO_KPI, "ctx", ctx);
        assertThat(result).isEqualTo(ReportAiCommentator.FALLBACK_COMMENTARY);
    }

    @Test
    @DisplayName("commentary retorna fallback para resposta vazia")
    void commentaryFallbackOnBlank() {
        GeminiAiService gemini = stubAnalyze((p, c) -> "");
        DefaultReportAiCommentator commentator = new DefaultReportAiCommentator(
            gemini, prompts, Executors.newSingleThreadExecutor(), Duration.ofSeconds(5));
        String result = commentator.commentary(ReportCode.CQ_OPERATIONAL_V2, "ctx", ctx);
        assertThat(result).isEqualTo(ReportAiCommentator.FALLBACK_COMMENTARY);
    }

    // ---------- T3: retry + circuit breaker ----------

    @Test
    @DisplayName("T3 — retenta ate 3x em timeout/erro transiente e retorna sucesso se 3a tentativa OK")
    void retriesThreeTimesOnTimeout() {
        AtomicInteger attempts = new AtomicInteger();
        GeminiAiService gemini = stubAnalyze((p, c) -> {
            int n = attempts.incrementAndGet();
            if (n < 3) {
                throw new RuntimeException("temporary-network-glitch");
            }
            return "Comentario apos retries.";
        });
        DefaultReportAiCommentator commentator = new DefaultReportAiCommentator(
            gemini, prompts, Executors.newSingleThreadExecutor(),
            Duration.ofSeconds(5), 3, Duration.ofMillis(1));
        String result = commentator.commentary(ReportCode.CQ_OPERATIONAL_V2, "ctx", ctx);
        assertThat(result).isEqualTo("Comentario apos retries.");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("T3 — NAO retenta em falha deterministica (BusinessException / API key)")
    void giveUpOnInvalidApiKeyImmediately() {
        AtomicInteger attempts = new AtomicInteger();
        GeminiAiService gemini = stubAnalyze((p, c) -> {
            attempts.incrementAndGet();
            throw new BusinessException("GEMINI_API_KEY nao configurada");
        });
        DefaultReportAiCommentator commentator = new DefaultReportAiCommentator(
            gemini, prompts, Executors.newSingleThreadExecutor(),
            Duration.ofSeconds(5), 3, Duration.ofMillis(1));
        String result = commentator.commentary(ReportCode.CQ_OPERATIONAL_V2, "ctx", ctx);
        assertThat(result).isEqualTo(ReportAiCommentator.FALLBACK_COMMENTARY);
        // Como e deterministico, apenas 1 tentativa.
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("T3 — circuit breaker abre apos threshold e retorna fallback direto")
    void circuitBreakerOpensAfterThreshold() {
        AtomicInteger attempts = new AtomicInteger();
        GeminiAiService gemini = stubAnalyze((p, c) -> {
            attempts.incrementAndGet();
            throw new RuntimeException("always-fails");
        });
        DefaultReportAiCommentator commentator = new DefaultReportAiCommentator(
            gemini, prompts, Executors.newSingleThreadExecutor(),
            Duration.ofMillis(200), 1, Duration.ofMillis(1));
        // Dispara 12 chamadas falhadas para passar do threshold de 10 com 60% falha
        for (int i = 0; i < 12; i++) {
            commentator.commentary(ReportCode.CQ_OPERATIONAL_V2, "ctx", ctx);
        }
        int afterOpen = attempts.get();
        // Proxima chamada deve bater no circuit aberto: nao chama mais gemini.
        String result = commentator.commentary(ReportCode.CQ_OPERATIONAL_V2, "ctx", ctx);
        assertThat(result).isEqualTo(ReportAiCommentator.FALLBACK_COMMENTARY);
        assertThat(attempts.get())
            .as("nao deve chamar Gemini quando circuit esta aberto")
            .isEqualTo(afterOpen);
    }

    private GeminiAiService stubAnalyze(java.util.function.BiFunction<String, String, String> fn) {
        return new GeminiAiService(null, null, "stub", "stub-model", 1024,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry()) {
            @Override
            public String analyze(String userPrompt, String context) {
                return fn.apply(userPrompt, context);
            }
        };
    }
}
