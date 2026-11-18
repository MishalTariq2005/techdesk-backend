package com.example.reporting_service.exception;

import com.example.reporting_service.async.ReportExecutionException;
import com.example.reporting_service.rbac.exception.InsufficientPermissionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InsufficientPermissionException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(InsufficientPermissionException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "FORBIDDEN", "message", e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "BAD_REQUEST", "message", e.getMessage()));
    }

    @ExceptionHandler(ReportExecutionException.class)
    public ResponseEntity<Map<String, String>> handleReportFailure(ReportExecutionException e) {
        // FIX: this handler was swallowing the real root cause completely —
        // the client got a bare 500 and NOTHING was ever logged server-side,
        // since Spring's default unhandled-exception logger only fires for
        // exceptions that reach it uncaught, not ones handled here. Logging
        // the cause explicitly now so this class of bug is diagnosable at all.
        log.error("Report execution failed: {}", e.getMessage(), e.getCause() != null ? e.getCause() : e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "REPORT_EXECUTION_FAILED", "message", String.valueOf(e.getMessage())));
    }

    // NEW: catch-all safety net — anything not explicitly handled above was
    // previously invisible too (falls through to Spring Boot's default error
    // page, which DOES log to console, but only after this advice class
    // declines to handle it — better to log it here explicitly and
    // consistently rather than rely on that fallback behavior).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        log.error("Unhandled exception in reporting-service", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "INTERNAL_ERROR", "message", String.valueOf(e.getMessage())));
    }
}