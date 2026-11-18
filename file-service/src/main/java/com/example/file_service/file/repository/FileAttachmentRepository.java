package com.example.file_service.file.repository;

import com.example.file_service.file.entity.FileAttachment;
import com.example.file_service.file.entity.FileParentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FileAttachmentRepository extends JpaRepository<FileAttachment, UUID> {
    List<FileAttachment> findByParentTypeAndParentIdOrderByCreatedAtDesc(FileParentType parentType, UUID parentId);
}
