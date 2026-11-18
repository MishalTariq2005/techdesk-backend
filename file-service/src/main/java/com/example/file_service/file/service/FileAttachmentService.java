package com.example.file_service.file.service;

import com.example.file_service.file.dto.FileMetadataResponse;
import com.example.file_service.file.entity.FileAttachment;
import com.example.file_service.file.entity.FileParentType;
import com.example.file_service.file.exception.FileNotFoundException;
import com.example.file_service.file.repository.FileAttachmentRepository;
import com.example.file_service.rbac.security.CurrentUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class FileAttachmentService {

    private final FileAttachmentRepository repository;
    private final FileStorageService storageService;
    private final FileAccessControlService accessControlService;

    public FileAttachmentService(FileAttachmentRepository repository,
                                  FileStorageService storageService,
                                  FileAccessControlService accessControlService) {
        this.repository = repository;
        this.storageService = storageService;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public FileMetadataResponse upload(FileParentType parentType, UUID parentId, MultipartFile file) {
        // Same rule enforced for upload as download — if you can't access
        // the parent's files, you can't attach one either.
        accessControlService.checkAccess(parentType, parentId);

        storageService.validate(file);
        String storagePath = storageService.store(file);

        UUID uploaderId = UUID.fromString(CurrentUserContext.get().userId());
        FileAttachment saved = repository.save(new FileAttachment(
            parentType, parentId, file.getOriginalFilename(), file.getContentType(),
            file.getSize(), storagePath, uploaderId));

        return toResponse(saved);
    }

    public List<FileMetadataResponse> listForParent(FileParentType parentType, UUID parentId) {
        accessControlService.checkAccess(parentType, parentId);
        return repository.findByParentTypeAndParentIdOrderByCreatedAtDesc(parentType, parentId).stream()
            .map(this::toResponse)
            .toList();
    }

    public FileMetadataResponse getMetadata(UUID fileId) {
        FileAttachment attachment = findOrThrow(fileId);
        accessControlService.checkAccess(attachment.getParentType(), attachment.getParentId());
        return toResponse(attachment);
    }

    /**
     * Returns the raw bytes AND the attachment metadata (needed by the
     * controller for Content-Type / Content-Disposition headers) — access
     * check happens here, before any bytes are read off disk, not as an
     * afterthought.
     */
    public FileDownload download(UUID fileId) {
        FileAttachment attachment = findOrThrow(fileId);
        accessControlService.checkAccess(attachment.getParentType(), attachment.getParentId());
        byte[] content = storageService.read(attachment.getStoragePath());
        return new FileDownload(attachment.getOriginalFilename(), attachment.getContentType(), content);
    }

    private FileAttachment findOrThrow(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new FileNotFoundException("File not found: " + id));
    }

    private FileMetadataResponse toResponse(FileAttachment a) {
        return new FileMetadataResponse(a.getId(), a.getParentType(), a.getParentId(),
            a.getOriginalFilename(), a.getContentType(), a.getSizeBytes(), a.getUploadedBy(), a.getCreatedAt());
    }

    public record FileDownload(String filename, String contentType, byte[] content) {}
}
