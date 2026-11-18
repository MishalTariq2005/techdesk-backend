package com.example.asset_service.exception;

import com.example.asset_service.asset.exception.AssetAlreadyAssignedException;
import com.example.asset_service.asset.exception.AssetNotFoundException;
import com.example.asset_service.asset.exception.DuplicateSerialNumberException;
import com.example.asset_service.gadget.exception.GadgetRequestNotFoundException;
import com.example.asset_service.gadget.exception.GadgetTypeNotFoundException;
import com.example.asset_service.gadget.exception.InvalidGadgetRequestTransitionException;
import com.example.asset_service.multitenancy.TenantIsolationException;
import com.example.asset_service.multitenancy.TenantNotResolvedException;
import com.example.asset_service.rbac.exception.InsufficientPermissionException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
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

    @ExceptionHandler(AssetNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleAssetNotFound(AssetNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateSerialNumberException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateSerial(DuplicateSerialNumberException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "CONFLICT", "message", ex.getMessage()));
    }

    /**
     * The double-assignment 409 — see Asset.status/AssetService.assign() and
     * AssetAlreadyAssignedException. Includes the current assignee's id/name
     * (name resolved via a live call to auth-service; see UserLookupClient)
     * and the date they were assigned, per the 5.1 challenge's requirement
     * for a "detailed error message." Same "exception carries the conflict
     * state" shape as ticket-service's TicketConflictException/currentState.
     */
    @ExceptionHandler(AssetAlreadyAssignedException.class)
    public ResponseEntity<Map<String, Object>> handleAssetAlreadyAssigned(AssetAlreadyAssignedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "CONFLICT");
        body.put("message", ex.getMessage());
        body.put("currentAssigneeId", ex.getCurrentAssigneeId());
        body.put("currentAssigneeName", ex.getCurrentAssigneeName()); // null if the auth-service lookup fell back
        body.put("assignedAt", ex.getAssignedAt());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Distinct from AssetAlreadyAssignedException above: that one fires when
     * the initial status check already sees ASSIGNED (the common case, with
     * full assignee detail). This one fires only in the genuine race window
     * — two requests both read AVAILABLE before either commits — caught by
     * Asset.version at the DB layer. Deliberately a plainer message: a fresh
     * re-query to attach assignee detail here would be extra complexity for
     * a rare edge case; asking the client to retry is enough; the retry will
     * hit the detailed AssetAlreadyAssignedException path instead.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleConcurrentModification(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "CONFLICT", "message",
                "This asset was modified concurrently by another request — please retry"));
    }

    @ExceptionHandler(GadgetRequestNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleGadgetRequestNotFound(GadgetRequestNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(GadgetTypeNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleGadgetTypeNotFound(GadgetTypeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
    }

    /**
     * Covers both an unrecognized target status AND a structurally-invalid
     * transition (e.g. approving a request that's already REJECTED) — same
     * single-exception-type shape as ticket-service's
     * InvalidStatusTransitionException -> 400.
     */
    @ExceptionHandler(InvalidGadgetRequestTransitionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidGadgetTransition(InvalidGadgetRequestTransitionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "INVALID_TRANSITION", "message", ex.getMessage()));
    }
}
