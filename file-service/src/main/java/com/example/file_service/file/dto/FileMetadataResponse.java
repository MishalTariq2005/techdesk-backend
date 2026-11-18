package com.example.file_service.file.dto;

import com.example.file_service.file.entity.FileParentType;

import java.time.Instant;
import java.util.UUID;

public record FileMetadataResponse(
    UUID id,
    FileParentType parentType,
    UUID parentId,
    String originalFilename,
    String contentType,
    long sizeBytes,
    UUID uploadedBy,
    Instant createdAt
) {}
