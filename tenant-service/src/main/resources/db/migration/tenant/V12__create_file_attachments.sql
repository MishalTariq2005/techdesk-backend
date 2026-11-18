-- V12: 5.3 — File Service (new standalone file-service, not folded into an
-- existing service — unlike 5.2's gadget-request decision, this one needs
-- to reference BOTH tickets (ticket-service) and gadget_requests
-- (asset-service) as parents, so no single existing service already owns
-- both concepts the way asset-service already owned Asset for 5.2).
--
-- Same convention as every migration since V7: lives in tenant-service's
-- set even though the code lives in file-service. Same reminder: existing
-- tenants need this applied manually via docker exec ... psql; new tenants
-- get it automatically via SchemaProvisioningService.

CREATE TABLE file_attachments (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_type        VARCHAR(20) NOT NULL,   -- 'TICKET' | 'GADGET_REQUEST'
    parent_id          UUID NOT NULL,          -- no FK: parent lives in a different service's
                                                 -- JPA mapping (ticket-service / asset-service),
                                                 -- even though the row physically sits in this
                                                 -- same tenant schema
    original_filename  VARCHAR(500) NOT NULL,
    content_type       VARCHAR(100) NOT NULL,
    size_bytes         BIGINT NOT NULL,
    storage_path       VARCHAR(500) NOT NULL UNIQUE,  -- relative path under app.file-storage.root,
                                                         -- tenant-namespaced (see FileStorageService)
    uploaded_by        UUID NOT NULL,          -- no FK: users live in auth-service's schema
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_file_attachments_parent ON file_attachments(parent_type, parent_id);
