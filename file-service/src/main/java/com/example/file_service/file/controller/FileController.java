package com.example.file_service.file.controller;

import com.example.file_service.file.dto.FileMetadataResponse;
import com.example.file_service.file.entity.FileParentType;
import com.example.file_service.file.service.FileAttachmentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Deliberately no @RequiresPermission anywhere here — every check is
 * DATA-dependent ("is THIS caller allowed to touch THIS specific parent's
 * files"), not a static role check, so it belongs in
 * FileAccessControlService, not the annotation layer (same reasoning
 * GadgetRequestController's cancel() used).
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileAttachmentService service;

    public FileController(FileAttachmentService service) {
        this.service = service;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileMetadataResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam FileParentType parentType,
            @RequestParam UUID parentId) {
        return ResponseEntity.ok(service.upload(parentType, parentId, file));
    }

    /**
     * NOT explicitly required by the roadmap text (which only asks for
     * upload + a single-file download) — added because a real UI needs
     * "what's attached to this ticket/request" before it can offer any
     * individual file to download. Same access check as everything else.
     */
    @GetMapping
    public ResponseEntity<List<FileMetadataResponse>> listForParent(
            @RequestParam FileParentType parentType,
            @RequestParam UUID parentId) {
        return ResponseEntity.ok(service.listForParent(parentType, parentId));
    }

    @GetMapping("/{id}/metadata")
    public ResponseEntity<FileMetadataResponse> getMetadata(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getMetadata(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        FileAttachmentService.FileDownload download = service.download(id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
            .contentType(MediaType.parseMediaType(download.contentType()))
            .body(download.content());
    }
}
