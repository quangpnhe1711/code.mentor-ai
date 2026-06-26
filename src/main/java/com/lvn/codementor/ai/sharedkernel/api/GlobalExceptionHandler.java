package com.lvn.codementor.ai.sharedkernel.api;

import com.lvn.codementor.ai.sharedkernel.error.AppException;
import com.lvn.codementor.ai.sharedkernel.error.ErrorCode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized translation of exceptions into the API error envelope (doc 08 §1/§2). Domain and data
 * layers never throw HTTP-typed exceptions; this advice is the single place that maps an
 * {@link AppException} to an HTTP status and {@code error.code}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        String requestId = UUID.randomUUID().toString();
        log.warn("AppException [{}] requestId={}: {}", ex.code(), requestId, ex.getMessage());
        ApiResponse<Void> body =
                ApiResponse.error(ex.code().name(), ex.getMessage(), ex.details(), requestId);
        return ResponseEntity.status(ex.code().status()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        String requestId = UUID.randomUUID().toString();
        // Internal detail is logged, never returned to the client (doc 08 §1: no leaking internals).
        log.error("Unhandled exception requestId={}", requestId, ex);
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.INTERNAL_ERROR.name(), "An unexpected error occurred.", java.util.List.of(), requestId);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(body);
    }
}
