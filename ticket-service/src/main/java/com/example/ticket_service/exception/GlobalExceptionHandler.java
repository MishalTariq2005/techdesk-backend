package com.example.ticket_service.exception;

import com.example.ticket_service.multitenancy.TenantIsolationException;
import com.example.ticket_service.multitenancy.TenantNotResolvedException;
import com.example.ticket_service.rbac.exception.InsufficientPermissionException;
import com.example.ticket_service.ticket.exception.InvalidStatusTransitionException;
import com.example.ticket_service.ticket.exception.TicketConflictException;
import com.example.ticket_service.ticket.exception.TicketNotFoundException;
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

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleTicketNotFound(TicketNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTransition(InvalidStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "INVALID_TRANSITION", "message", ex.getMessage()));
    }

    /**
     * The optimistic-locking 409 — see Ticket.version and TicketService.updateStatus.
     * Includes currentState so the client can show the caller what actually
     * changed instead of just "conflict, try again."
     */
    @ExceptionHandler(TicketConflictException.class)
    public ResponseEntity<Map<String, Object>> handleTicketConflict(TicketConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "CONFLICT", "message", ex.getMessage(), "currentState", ex.getCurrentState()));
    }
}
