package com.bioqc.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Filtro global que garante um {@code correlationId} unico por request,
 * propagado em:
 * <ul>
 *   <li>MDC — para que appenders (LogstashEncoder em prod) emitam o campo
 *       em cada linha de log;</li>
 *   <li>Response header {@code X-Correlation-Id} — para que o cliente
 *       possa correlacionar logs do servidor com a UI;</li>
 *   <li>Request attribute {@code correlationId} — para que services
 *       (ex.: {@code ReportServiceV2}) possam ler e embutir em
 *       {@code GenerationContext}.</li>
 * </ul>
 *
 * <p>Se o cliente mandar {@code X-Correlation-Id} no request, respeitamos
 * (aceitamos qualquer string de 1..128 caracteres sanitizada). Caso contrario,
 * geramos UUID encurtado para 8 chars (primeiro bloco). 8 chars e suficiente
 * para correlacionar num request trace e mais legivel em logs.
 *
 * <p>Ordem: {@link Ordered#HIGHEST_PRECEDENCE} + 10 — roda antes de praticamente
 * todos os filters de negocio mas depois de filters reservados de infra
 * (tracing do Actuator, se houver).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter implements Filter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String ATTRIBUTE = "correlationId";

    private static final int MAX_LENGTH = 128;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        String correlationId = resolve((HttpServletRequest) request);
        try {
            MDC.put(MDC_KEY, correlationId);
            request.setAttribute(ATTRIBUTE, correlationId);
            if (response instanceof HttpServletResponse http) {
                http.setHeader(HEADER, correlationId);
            }
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolve(HttpServletRequest request) {
        String incoming = request.getHeader(HEADER);
        if (incoming != null) {
            String sanitized = sanitize(incoming);
            if (!sanitized.isEmpty()) return sanitized;
        }
        // UUID v4 encurtado para os primeiros 8 chars (1e9 combinacoes dentro
        // do horizonte de um dia — colisao irrelevante para correlacao).
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String sanitize(String value) {
        // Apenas [A-Za-z0-9._-] ate MAX_LENGTH. Limita superficie de log
        // injection e preserva legibilidade.
        StringBuilder sb = new StringBuilder(Math.min(value.length(), MAX_LENGTH));
        int i = 0;
        for (char c : value.toCharArray()) {
            if (i++ >= MAX_LENGTH) break;
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
