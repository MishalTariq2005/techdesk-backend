package com.example.file_service.file.service;

import com.example.file_service.file.exception.FileTooLargeException;
import com.example.file_service.file.exception.UnsupportedFileTypeException;
import com.example.file_service.multitenancy.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Roadmap 5.3 says "local volume (or S3-compatible MinIO)" — this project's
 * docker-compose.yml has no MinIO container, and adding one is real new
 * infrastructure not currently in the stack, so this defaults to local
 * volume storage: a named Docker volume (see docker-compose.yml's
 * file_storage_data), same pattern already used for postgres_data.
 *
 * Files are namespaced by tenant schema on disk (app.file-storage.root/
 * {tenantSchema}/{uuid}) — defense in depth, matching this project's
 * general layered-security habit (e.g. RBAC's three independent
 * enforcement layers). The DB row already scopes access correctly via
 * schema-per-tenant, but a raw filesystem read of the shared volume
 * shouldn't be able to cross tenants either, even though nothing in this
 * service currently exposes raw filesystem access.
 *
 * KNOWN LIMITATION, flagged rather than silently glossed over: MIME
 * validation here trusts MultipartFile.getContentType() — the
 * browser/client-declared Content-Type header — not the actual file bytes.
 * A malicious upload could declare "image/png" while the bytes are
 * something else entirely (no magic-byte sniffing implemented). Real
 * hardening would inspect the actual byte signature; out of scope for what
 * 5.3 asks, but worth carrying forward to Week 4's security review (6.4).
 */
@Service
public class FileStorageService {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10MB, per the roadmap's explicit limit

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif",
        "application/pdf",
        "text/plain"
    );

    private final Path storageRoot;

    public FileStorageService(@Value("${app.file-storage.root}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot);
    }

    public void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload an empty file");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new FileTooLargeException(
                "File exceeds the 10MB limit (was " + file.getSize() + " bytes)");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new UnsupportedFileTypeException(
                "Unsupported file type: " + contentType + ". Allowed: " + ALLOWED_CONTENT_TYPES);
        }
    }

    /**
     * Returns the RELATIVE storage path (tenant-namespaced), to be persisted
     * on FileAttachment.storagePath — never the absolute path, so the
     * storage root can move without invalidating existing rows.
     */
    public String store(MultipartFile file) {
        String tenant = TenantContext.getCurrentTenant();
        String generatedName = UUID.randomUUID().toString();
        String relativePath = tenant + "/" + generatedName;

        Path targetDir = storageRoot.resolve(tenant);
        Path targetFile = storageRoot.resolve(relativePath);

        try {
            Files.createDirectories(targetDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }

        return relativePath;
    }

    public byte[] read(String relativePath) {
        Path targetFile = storageRoot.resolve(relativePath);
        try {
            return Files.readAllBytes(targetFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read stored file: " + relativePath, e);
        }
    }
}
