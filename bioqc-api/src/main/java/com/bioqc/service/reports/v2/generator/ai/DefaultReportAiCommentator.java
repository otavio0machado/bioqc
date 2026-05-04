package com.bioqc.service.reports.v2.generator.ai;

import com.bioqc.exception.BusinessException;
import com.bioqc.service.GeminiAiService;
import com.bioqc.service.reports.v2.catalog.ReportCode;
import com.bioqc.service.reports.v2.generator.GenerationContext;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Implementacao padrao de {@link ReportAiCommentator}. Submete a chamada ao
 * {@link GeminiAiService} num executor dedicado com timeout de 15s por
 * tentativa, ate 3 tentativas com backoff exponencial (200ms, 600ms, 1800ms)
 * quando a falha for retriavel. Falhas determinnsticas (API key invalida,
 * rate limit, resposta vazia ou mensagem amigavel do Gemini) NAO retentam.
 *
 * <p>Circuit-breaker: janela deslizante de 5 min; se taxa &gt; 60% falha em
 * 10+ requests, abre por 2 min retornando fallback sem chamar Gemini.
 *
 * <p>Qualquer falha (excecao, timeout, circuit aberto, mensagem amigavel)
 * resulta em {@link #FALLBACK_COMMENTARY} — o relatorio sempre pode ser
 * emitido; a analise textual e um plus, nao requisito regulatorio.
 *
 * <p>Observabilidade: cada tentativa loga um evento estruturado com
 * {@code correlationId} (do MDC), {@code attempt}, {@code latencyMs} e
 * {@code outcome} (success, retry, circuit-open, timeout, fallback,
 * giveup-deterministic).
 */
@Component
public class DefaultReportAiCommentator implements ReportAiCommentator {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultReportAiCommentator.class);

    private static final String GEMINI_FRIENDLY_PREFIX = "Nao foi possivel analisar";
    private static final String GEMINI_FRIENDLY_PREFIX_ACCENT = "Não foi possível analisar";

    /** Timeout padrao por tentativa (15s). */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    /** Numero maximo de tentativas (incluindo a primeira). */
    private static final int MAX_ATTEMPTS = 3;
    /** Delay base do backoff (multiplicado por 3^(attempt-1)). */
    private static final Duration BASE_BACKOFF = Duration.ofMillis(200);

    // ---------- Circuit breaker ----------
    /** Janela deslizante em millis. */
    private static final long CB_WINDOW_MS = Duration.ofMinutes(5).toMillis();
    /** Numero minimo de requests na janela para avaliar abertura. */
    private static final int CB_MIN_REQUESTS = 10;
    /** Taxa de falha (0..1) acima da qual abrimos. */
    private static final double CB_FAIL_RATE_THRESHOLD = 0.60;
    /** Duracao do estado aberto em millis. */
    private static final long CB_OPEN_MS = Duration.ofMinutes(2).toMillis();

    private final GeminiAiService geminiAiService;
    private final ReportAiPrompts prompts;
    private final ExecutorService executor;
    private final Duration timeout;
    private final int maxAttempts;
    private final Duration baseBackoff;

    // Contadores do circuit breaker (protegidos por locks implicitos
    // atomicos — window snapshot em [windowStart, windowStart+CB_WINDOW_MS])
    private final AtomicLong cbWindowStart = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger cbTotal = new AtomicInteger();
    private final AtomicInteger cbFailures = new AtomicInteger();
    private final AtomicLong cbOpenUntil = new AtomicLong(0L);

    @Autowired
    public DefaultReportAiCommentator(GeminiAiService geminiAiService, ReportAiPrompts prompts) {
        this(geminiAiService, prompts, buildDefaultExecutor(), DEFAULT_TIMEOUT, MAX_ATTEMPTS, BASE_BACKOFF);
    }

    /** Construtor para testes — permite injetar executor, timeout, attempts, backoff. */
    public DefaultReportAiCommentator(
        GeminiAiService geminiAiService,
        ReportAiPrompts prompts,
        ExecutorService executor,
        Duration timeout
    ) {
        this(geminiAiService, prompts, executor, timeout, MAX_ATTEMPTS, BASE_BACKOFF);
    }

    /** Construtor completo para testes. */
    public DefaultReportAiCommentator(
        GeminiAiService geminiAiService,
        ReportAiPrompts prompts,
        ExecutorService executor,
        Duration timeout,
        int maxAttempts,
        Duration baseBackoff
    ) {
        this.geminiAiService = geminiAiService;
        this.prompts = prompts;
        this.executor = executor;
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        this.maxAttempts = maxAttempts <= 0 ? 1 : maxAttempts;
        this.baseBackoff = baseBackoff == null ? BASE_BACKOFF : baseBackoff;
    }

    @Override
    public String commentary(ReportCode code, String structuredContext, GenerationContext ctx) {
        String prompt = prompts.promptFor(code);
        String context = structuredContext == null ? "" : structuredContext;
        String correlationId = resolveCorrelationId(ctx);

        // Circuit breaker check
        if (isCircuitOpen()) {
            logStructured(correlationId, code, 0, 0L, "circuit-open", null);
            return FALLBACK_COMMENTARY;
        }

        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startMs = System.currentTimeMillis();
            CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> geminiAiService.analyze(prompt, context), executor);
            try {
                String result = future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join();
                long latency = System.currentTimeMillis() - startMs;
                if (result == null || result.isBlank()) {
                    // Resposta vazia: deterministico, nao retentamos.
                    recordOutcome(false);
                    logStructured(correlationId, code, attempt, latency, "empty-response", null);
                    return FALLBACK_COMMENTARY;
                }
                String trimmed = result.trim();
                if (trimmed.startsWith(GEMINI_FRIENDLY_PREFIX) || trimmed.startsWith(GEMINI_FRIENDLY_PREFIX_ACCENT)) {
                    // Mensagem amigavel do proprio GeminiAiService (ja tratou
                    // exception internamente). Deterministico — nao retenta.
                    recordOutcome(false);
                    logStructured(correlationId, code, attempt, latency, "gemini-friendly-error", null);
                    return FALLBACK_COMMENTARY;
                }
                recordOutcome(true);
                logStructured(correlationId, code, attempt, latency, "success", null);
                return trimmed;
            } catch (CompletionException ex) {
                long latency = System.currentTimeMillis() - startMs;
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                lastError = cause instanceof RuntimeException re ? re : new RuntimeException(cause);
                String outcome = cause instanceof TimeoutException ? "timeout" : "exception";
                if (isDeterministicError(cause)) {
                    // API key / rate limit / business — nao faz sentido retentar
                    recordOutcome(false);
                    logStructured(correlationId, code, attempt, latency, "giveup-deterministic", cause.getMessage());
                    return FALLBACK_COMMENTARY;
                }
                recordOutcome(false);
                if (attempt < maxAttempts) {
                    logStructured(correlationId, code, attempt, latency, outcome + "-retry", cause.getMessage());
                    sleepBackoff(attempt);
                } else {
                    logStructured(correlationId, code, attempt, latency, outcome + "-giveup", cause.getMessage());
                }
            } catch (RuntimeException ex) {
                long latency = System.currentTimeMillis() - startMs;
                lastError = ex;
                if (isDeterministicError(ex)) {
                    recordOutcome(false);
                    logStructured(correlationId, code, attempt, latency, "giveup-deterministic", ex.getMessage());
                    return FALLBACK_COMMENTARY;
                }
                recordOutcome(false);
                if (attempt < maxAttempts) {
                    logStructured(correlationId, code, attempt, latency, "exception-retry", ex.getMessage());
                    sleepBackoff(attempt);
                } else {
                    logStructured(correlationId, code, attempt, latency, "exception-giveup", ex.getMessage());
                }
            }
        }
        if (lastError != null) {
            LOG.warn("Gemini falhou apos {} tentativas para code={}: {}", maxAttempts, code,
                lastError.getMessage());
        }
        return FALLBACK_COMMENTARY;
    }

    // ---------- Circuit breaker ----------

    private boolean isCircuitOpen() {
        long now = System.currentTimeMillis();
        long openUntil = cbOpenUntil.get();
        if (openUntil > now) return true;
        // Reset janela se expirou
        long windowStart = cbWindowStart.get();
        if (now - windowStart > CB_WINDOW_MS) {
            if (cbWindowStart.compareAndSet(windowStart, now)) {
                cbTotal.set(0);
                cbFailures.set(0);
            }
        }
        return false;
    }

    private void recordOutcome(boolean success) {
        long now = System.currentTimeMillis();
        long windowStart = cbWindowStart.get();
        if (now - windowStart > CB_WINDOW_MS) {
            if (cbWindowStart.compareAndSet(windowStart, now)) {
                cbTotal.set(0);
                cbFailures.set(0);
            }
        }
        int total = cbTotal.incrementAndGet();
        if (!success) cbFailures.incrementAndGet();
        int failures = cbFailures.get();
        if (total >= CB_MIN_REQUESTS && (failures * 1.0 / total) > CB_FAIL_RATE_THRESHOLD) {
            long target = now + CB_OPEN_MS;
            cbOpenUntil.updateAndGet(prev -> Math.max(prev, target));
            LOG.warn("Circuit breaker Gemini ABERTO: {}/{} falhas na janela — reativa em {}s",
                failures, total, CB_OPEN_MS / 1000);
        }
    }

    /** Visibilidade para testes — inspeciona/reseta estado do circuit breaker. */
    void resetCircuitBreakerForTests() {
        cbWindowStart.set(System.currentTimeMillis());
        cbTotal.set(0);
        cbFailures.set(0);
        cbOpenUntil.set(0L);
    }

    // ---------- Retry helpers ----------

    private boolean isDeterministicError(Throwable t) {
        if (t == null) return false;
        // BusinessException do GeminiAiService indica rate-limit ou API key
        // nao configurada — retentar nao adianta e apenas gasta cota.
        if (t instanceof BusinessException) return true;
        String msg = t.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("api key") || lower.contains("api_key")
            || lower.contains("unauthorized") || lower.contains("forbidden")
            || lower.contains("limite de")
            || lower.contains("gemini_api_key");
    }

    private void sleepBackoff(int attempt) {
        // Backoff exponencial: 200ms, 600ms, 1800ms (para attempts 1,2,3)
        long delayMs = (long) (baseBackoff.toMillis() * Math.pow(3, attempt - 1));
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String resolveCorrelationId(GenerationContext ctx) {
        if (ctx != null && ctx.correlationId() != null) return ctx.correlationId();
        String fromMdc = MDC.get("correlationId");
        return fromMdc != null ? fromMdc : "n/a";
    }

    private void logStructured(String correlationId, ReportCode code, int attempt,
                               long latencyMs, String outcome, String detail) {
        // Tenta colocar correlationId no MDC para que o logback JSON encoder
        // emita o campo mesmo quando chamada nao veio do filter web.
        String existing = MDC.get("correlationId");
        boolean pushed = false;
        if ((existing == null || existing.isBlank()) && correlationId != null) {
            MDC.put("correlationId", correlationId);
            pushed = true;
        }
        try {
            if (detail == null) {
                LOG.info("ai.call correlationId={} code={} attempt={} latencyMs={} outcome={}",
                    correlationId, code, attempt, latencyMs, outcome);
            } else {
                LOG.info("ai.call correlationId={} code={} attempt={} latencyMs={} outcome={} detail={}",
                    correlationId, code, attempt, latencyMs, outcome, detail);
            }
        } finally {
            if (pushed) MDC.remove("correlationId");
        }
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    private static ExecutorService buildDefaultExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "report-ai-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return Executors.newFixedThreadPool(2, factory);
    }
}
