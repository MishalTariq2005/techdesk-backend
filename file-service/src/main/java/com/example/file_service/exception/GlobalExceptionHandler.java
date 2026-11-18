package com.example.file_service.exception;

import com.example.file_service.file.exception.FileAccessDeniedException;
import com.example.file_service.file.exception.FileNotFoundException;
import com.example.file_service.file.exception.FileTooLargeException;
import com.example.file_service.file.exception.UnsupportedFileTypeException;
import com.example.file_service.multitenancy.TenantIsolationException;
import com.example.file_service.multitenancy.TenantNotResolvedException;
import com.example.file_service.rbac.exception.InsufficientPermissionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleFileNotFound(FileNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<Map<String, String>> handleFileTooLarge(FileTooLargeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "FILE_TOO_LARGE", "message", ex.getMessage()));
    }

    /**
     * Belt-and-suspenders alongside FileStorageService's own 10MB check:
     * Spring's multipart parser itself enforces spring.servlet.multipart.max-file-size
     * (see application.yml) BEFORE the request body is even fully read into
     * a MultipartFile — for a sufficiently oversized upload, THIS is what
     * actually fires, not FileTooLargeException.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "FILE_TOO_LARGE", "message", "File exceeds the 10MB limit"));
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<Map<String, String>> handleUnsupportedFileType(UnsupportedFileTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "UNSUPPORTED_FILE_TYPE", "message", ex.getMessage()));
    }

    /**
     * The 5.3 challenge's core requirement: cross-tenant / unauthorized file
     * access returns 403. See FileAccessControlService for where this
     * actually gets thrown.
     */
    @ExceptionHandler(FileAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleFileAccessDenied(FileAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", "FORBIDDEN", "message", ex.getMessage()));
    }
}
