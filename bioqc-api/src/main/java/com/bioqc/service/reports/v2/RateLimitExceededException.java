package com.bioqc.service.reports.v2;

/**
 * Disparado quando o limite de requisicoes por janela e excedido. Mapeia para
 * HTTP 429 com header {@code Retry-After}.
 */
public class RateLimitExceededException extends RuntimeException {

    private final int retryAfterSeconds;

    public RateLimitExceededException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
