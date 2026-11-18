package com.example.notification_service.exception;

import com.example.notification_service.multitenancy.TenantIsolationException;
import com.example.notification_service.multitenancy.TenantNotResolvedException;
import com.example.notification_service.notification.exception.NotificationNotFoundException;
import com.example.notification_service.rbac.exception.InsufficientPermissionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientPermissionException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientPermission(InsufficientPermissionException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", "FORBIDDEN", "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "BAD_REQUEST", "message", ex.getMessage()));
    }

    @ExceptionHandler(TenantIsolationException.class)
    public ResponseEntity<Map<String, String>> handleTenantIsolation(TenantIsolationException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "TENANT_ISOLATION_BREACH", "message", ex.getMessage()));
    }

    @ExceptionHandler(TenantNotResolvedException.class)
    public ResponseEntity<Map<String, String>> handleTenantNotResolved(TenantNotResolvedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "TENANT_NOT_RESOLVED", "message", ex.getMessage()));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotificationNotFound(NotificationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
    }
}
