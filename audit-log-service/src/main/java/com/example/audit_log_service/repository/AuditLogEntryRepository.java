package com.example.audit_log_service.repository;

import com.example.audit_log_service.entity.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogEntryRepository
        extends JpaRepository<AuditLogEntry, Long>, JpaSpecificationExecutor<AuditLogEntry> {
    // Filtering by entity_type / actor_id / date range uses Specifications
    // (AuditQueryService) rather than derived-query method explosion, since
    // the roadmap's filter combination is open-ended (any subset of the
    // three). Page<T> findAll(Specification<T>, Pageable) comes for free
    // from JpaSpecificationExecutor — no need to redeclare it here.
}
