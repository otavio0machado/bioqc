package com.bioqc.service.reports.v2;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Rate limiter por chave + janela de 1 minuto. Usa {@link ConcurrentHashMap}
 * interno (sem dependencias externas — Caffeine seria overkill para este
 * caso). A janela avanca naturalmente: cada bucket e descartado/recriado
 * quando seu timestamp de inicio e &gt; 60s no passado.
 *
 * <p><strong>Nao</strong> persiste entre restarts e nao compartilha estado
 * entre instancias. Aceitavel para V2 single-node. Em multi-node use um
 * limiter distribuido antes de ir para producao multi-replica.
 */
@Component
public class InMemoryRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int limitPerMinute) {
        if (limitPerMinute <= 0) return true;
        if (key == null) throw new IllegalArgumentException("key obrigatoria");
        Instant now = Instant.now();
        Bucket bucket = buckets.compute(key, (k, existing) -> {
            if (existing == null || Duration.between(existing.windowStart, now).compareTo(WINDOW) >= 0) {
                return new Bucket(now, new AtomicInteger(0));
            }
            return existing;
        });
        int current = bucket.count.incrementAndGet();
        return current <= limitPerMinute;
    }

    /**
     * Segundos restantes ate a janela atual expirar para {@code key}. Util para
     * montar o header {@code Retry-After}.
     */
    public int secondsUntilReset(String key) {
        Bucket b = buckets.get(key);
        if (b == null) return 0;
        long elapsed = Duration.between(b.windowStart, Instant.now()).toSeconds();
        long remaining = WINDOW.toSeconds() - elapsed;
        return (int) Math.max(remaining, 1);
    }

    // visivel para testes
    void clear() {
        buckets.clear();
    }

    private record Bucket(Instant windowStart, AtomicInteger count) {}
}
