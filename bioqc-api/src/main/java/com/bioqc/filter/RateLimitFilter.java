package com.bioqc.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String TOO_MANY_REQUESTS_MESSAGE =
        "Muitas tentativas de login. Aguarde antes de tentar novamente.";

    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final long windowSeconds;
    private final MeterRegistry meterRegistry;
    private final Map<String, Deque<Instant>> failedAttemptsByClient = new ConcurrentHashMap<>();

    public RateLimitFilter(
        ObjectMapper objectMapper,
        @Value("${app.auth.login-rate-limit.max-attempts:5}") int maxAttempts,
        @Value("${app.auth.login-rate-limit.window-seconds:60}") long windowSeconds,
        MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !LOGIN_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String clientKey = resolveClientKey(request);
        if (hasExceededRateLimit(clientKey)) {
            recordLoginAttempt("rate_limited");
            writeRateLimitResponse(response);
            return;
        }

        filterChain.doFilter(request, response);
        updateAttempts(clientKey, response.getStatus());

        int status = response.getStatus();
        if (status == HttpStatus.BAD_REQUEST.value() || status == HttpStatus.UNAUTHORIZED.value()) {
            recordLoginAttempt("failure");
        } else if (status < HttpStatus.BAD_REQUEST.value()) {
            recordLoginAttempt("success");
        }
    }

    private boolean hasExceededRateLimit(String clientKey) {
        Deque<Instant> attempts = failedAttemptsByClient.computeIfAbsent(clientKey, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            pruneOldAttempts(attempts, Instant.now());
            if (attempts.isEmpty()) {
                failedAttemptsByClient.remove(clientKey, attempts);
                return false;
            }
            return attempts.size() >= maxAttempts;
        }
    }

    private void updateAttempts(String clientKey, int responseStatus) {
        Deque<Instant> attempts = failedAttemptsByClient.computeIfAbsent(clientKey, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            Instant now = Instant.now();
            pruneOldAttempts(attempts, now);
            if (responseStatus == HttpStatus.BAD_REQUEST.value() || responseStatus == HttpStatus.UNAUTHORIZED.value()) {
                attempts.addLast(now);
            } else if (responseStatus < HttpStatus.BAD_REQUEST.value()) {
                attempts.clear();
            }

            if (attempts.isEmpty()) {
                failedAttemptsByClient.remove(clientKey, attempts);
            }
        }
    }

    private void pruneOldAttempts(Deque<Instant> attempts, Instant now) {
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(now.minusSeconds(windowSeconds))) {
            attempts.pollFirst();
        }
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void recordLoginAttempt(String result) {
        Counter.builder("bioqc.auth.login.attempts")
            .description("Number of login attempts")
            .tag("result", result)
            .register(meterRegistry)
            .increment();
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(windowSeconds));
        objectMapper.writeValue(response.getWriter(), Map.of(
            "timestamp", Instant.now().toString(),
            "status", HttpStatus.TOO_MANY_REQUESTS.value(),
            "error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
            "message", TOO_MANY_REQUESTS_MESSAGE,
            "fields", Map.of()
        ));
    }
}
