package com.bioqc.exception;

import com.bioqc.service.reports.v2.InvalidFilterException;
import com.bioqc.service.reports.v2.InvalidSignerException;
import com.bioqc.service.reports.v2.RateLimitExceededException;
import com.bioqc.service.reports.v2.ReportAlreadySignedException;
import com.bioqc.service.reports.v2.ReportCodeNotFoundException;
import com.bioqc.service.reports.v2.ReportExpiredException;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handlers especificos para excecoes Reports V2. Usa {@link ProblemDetail}
 * (RFC 7807). Registrado apenas quando {@code reports.v2.enabled=true} para
 * nao conflitar com setups sem a feature.
 *
 * <p>Este advice tem precedencia sobre {@code GlobalExceptionHandler}
 * (@Order alto ganha prioridade sobre o default).
 */
@RestControllerAdvice
@ConditionalOnProperty(prefix = "reports.v2", name = "enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReportsV2ExceptionHandler {

    private static final URI TYPE_REPORT_CODE = URI.create("https://bioqc.local/errors/reports-v2/code-not-found");
    private static final URI TYPE_INVALID_FILTER = URI.create("https://bioqc.local/errors/reports-v2/invalid-filter");
    private static final URI TYPE_RATE_LIMIT = URI.create("https://bioqc.local/errors/reports-v2/rate-limit");
    private static final URI TYPE_ALREADY_SIGNED = URI.create("https://bioqc.local/errors/reports-v2/already-signed");
    private static final URI TYPE_EXPIRED = URI.create("https://bioqc.local/errors/reports-v2/expired");
    private static final URI TYPE_INVALID_SIGNER = URI.create("https://bioqc.local/errors/reports-v2/invalid-signer");

    @ExceptionHandler(ReportCodeNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleCodeNotFound(ReportCodeNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Codigo de relatorio nao encontrado");
        pd.setType(TYPE_REPORT_CODE);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @ExceptionHandler(InvalidFilterException.class)
    public ResponseEntity<ProblemDetail> handleInvalidFilter(InvalidFilterException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setTitle("Filtros invalidos");
        pd.setType(TYPE_INVALID_FILTER);
        pd.setProperty("violations", ex.getViolations());
        return ResponseEntity.unprocessableEntity().body(pd);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        pd.setTitle("Limite de requisicoes excedido");
        pd.setType(TYPE_RATE_LIMIT);
        pd.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers).body(pd);
    }

    @ExceptionHandler(ReportAlreadySignedException.class)
    public ResponseEntity<ProblemDetail> handleAlreadySigned(ReportAlreadySignedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Relatorio ja assinado");
        pd.setType(TYPE_ALREADY_SIGNED);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler(ReportExpiredException.class)
    public ResponseEntity<ProblemDetail> handleExpired(ReportExpiredException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
        pd.setTitle("Relatorio expirado");
        pd.setType(TYPE_EXPIRED);
        return ResponseEntity.status(HttpStatus.GONE).body(pd);
    }

    @ExceptionHandler(InvalidSignerException.class)
    public ResponseEntity<ProblemDetail> handleInvalidSigner(InvalidSignerException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setTitle("Responsavel tecnico invalido");
        pd.setType(TYPE_INVALID_SIGNER);
        return ResponseEntity.unprocessableEntity().body(pd);
    }
}
