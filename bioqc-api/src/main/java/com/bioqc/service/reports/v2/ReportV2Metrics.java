package com.bioqc.service.reports.v2;

import com.bioqc.service.reports.v2.catalog.ReportCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Expoe metricas Prometheus para operacoes de Reports V2. Consumido pelo
 * endpoint {@code /actuator/prometheus} (ja configurado no projeto).
 *
 * <p><b>Metricas expostas</b>:
 * <ul>
 *   <li>{@code reports_v2_generation_total{code, outcome}} — contagem de
 *       geracoes por tipo e desfecho ({@code success|failure})</li>
 *   <li>{@code reports_v2_generation_duration_seconds{code}} — histograma
 *       de duracao para cada tipo</li>
 *   <li>{@code reports_v2_signature_total{code, outcome}} — assinaturas</li>
 *   <li>{@code reports_v2_download_total{code, version}} — downloads por
 *       tipo e versao servida ({@code original|signed})</li>
 *   <li>{@code reports_v2_ai_call_total{code, outcome}} — chamadas Gemini
 *       ({@code success|fallback|timeout})</li>
 * </ul>
 *
 * <p>Contadores cacheados em {@link ConcurrentHashMap} para evitar recriacao
 * a cada incremento — Micrometer ja deduplica, mas cache local reduz
 * alocacao em hot-path.
 */
@Component
public class ReportV2Metrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();

    public ReportV2Metrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordGeneration(ReportCode code, boolean success, Duration duration) {
        String outcome = success ? "success" : "failure";
        counter("reports_v2_generation_total", "code", safeCode(code), "outcome", outcome).increment();
        timer("reports_v2_generation_duration_seconds", "code", safeCode(code)).record(duration);
    }

    public void recordSignature(ReportCode code, boolean success) {
        String outcome = success ? "success" : "failure";
        counter("reports_v2_signature_total", "code", safeCode(code), "outcome", outcome).increment();
    }

    public void recordDownload(ReportCode code, String version) {
        counter("reports_v2_download_total", "code", safeCode(code), "version", version).increment();
    }

    public void recordAiCall(ReportCode code, String outcome) {
        counter("reports_v2_ai_call_total", "code", safeCode(code), "outcome", outcome).increment();
    }

    // ---------- internals ----------

    private Counter counter(String name, String... tags) {
        String key = cacheKey(name, tags);
        return counters.computeIfAbsent(key, k ->
            Counter.builder(name).tags(tags).register(registry)
        );
    }

    private Timer timer(String name, String... tags) {
        String key = cacheKey(name, tags);
        return timers.computeIfAbsent(key, k ->
            Timer.builder(name).tags(tags)
                .publishPercentileHistogram(true)
                .register(registry)
        );
    }

    private static String cacheKey(String name, String... tags) {
        StringBuilder sb = new StringBuilder(name);
        for (String t : tags) sb.append('|').append(t);
        return sb.toString();
    }

    private static String safeCode(ReportCode code) {
        return code == null ? "UNKNOWN" : code.name();
    }
}
