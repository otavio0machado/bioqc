package com.bioqc.service.reports.v2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    @Test
    @DisplayName("tryAcquire libera ate o limite e bloqueia acima")
    void allowsUpToLimit() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        String key = "user:alice";
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(key, 5)).isTrue();
        }
        assertThat(limiter.tryAcquire(key, 5)).isFalse();
    }

    @Test
    @DisplayName("chaves diferentes tem buckets independentes")
    void differentKeysAreIndependent() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        for (int i = 0; i < 3; i++) assertThat(limiter.tryAcquire("a", 3)).isTrue();
        assertThat(limiter.tryAcquire("a", 3)).isFalse();
        assertThat(limiter.tryAcquire("b", 3)).isTrue();
    }

    @Test
    @DisplayName("limit <= 0 sempre libera")
    void zeroLimitAllowsAll() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        for (int i = 0; i < 100; i++) assertThat(limiter.tryAcquire("x", 0)).isTrue();
    }

    @Test
    @DisplayName("secondsUntilReset retorna <= 60")
    void resetsAfterWindow() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        limiter.tryAcquire("z", 1);
        int secs = limiter.secondsUntilReset("z");
        assertThat(secs).isBetween(1, 60);
    }
}
