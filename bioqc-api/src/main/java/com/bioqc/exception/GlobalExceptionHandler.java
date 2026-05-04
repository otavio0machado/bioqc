package com.bioqc.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException exception) {
        return ResponseEntity.badRequest()
            .body(ApiError.of(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fields.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
            .body(ApiError.of(HttpStatus.BAD_REQUEST, "Dados inválidos", fields));
    }

    // Corpo malformado: Jackson falha ao desserializar (ex: "" para LocalDate).
    // Sem este handler o Spring devolve 400 sem body, e o frontend mostra apenas
    // "Request failed with status code 400" — sem pista do campo culpado.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
            .body(ApiError.of(
                HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido. Verifique os campos enviados (datas devem estar no formato yyyy-MM-dd ou ausentes)."
            ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiError.of(HttpStatus.FORBIDDEN, "Acesso negado"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno inesperado"
            ));
    }

    public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fields
    ) {
        public static ApiError of(HttpStatus status, String message) {
            return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, Map.of());
        }

        public static ApiError of(HttpStatus status, String message, Map<String, String> fields) {
            return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, fields);
        }
    }
}
