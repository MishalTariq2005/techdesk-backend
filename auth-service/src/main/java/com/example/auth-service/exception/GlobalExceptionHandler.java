package com.example.auth_service.exception;
import com.example.auth_service.rbac.exception.InsufficientPermissionException;


import com.example.auth_service.multitenancy.TenantIsolationException;
import com.example.auth_service.multitenancy.TenantNotResolvedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TenantIsolationException.class)
    public ResponseEntity<Map<String, Object>> handleTenantIsolation(TenantIsolationException ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(TenantNotResolvedException.class)
    public ResponseEntity<Map<String, Object>> handleTenantNotResolved(TenantNotResolvedException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    @ExceptionHandler(InsufficientPermissionException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientPermission(InsufficientPermissionException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", "FORBIDDEN", "message", ex.getMessage()));
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}