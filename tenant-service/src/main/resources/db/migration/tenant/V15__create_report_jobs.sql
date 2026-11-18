-- V15: 6.2 Reporting Service async job backing table. Report requests are
-- tenant-scoped data (same tenant's tickets/assets), so report_jobs lives
-- per-tenant schema like everything else, not in a shared "reporting"
-- schema — reporting-service reads it through the exact same schema-per-
-- tenant Hibernate setup ticket-service/asset-service already use.

CREATE TABLE report_jobs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_type   VARCHAR(50) NOT NULL,
    requested_by  UUID NOT NULL,
    params        JSONB,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | READY | FAILED
    result        JSONB,
    error_message VARCHAR(500),
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    completed_at  TIMESTAMP
);

CREATE INDEX idx_report_jobs_status ON report_jobs(status);
CREATE INDEX idx_report_jobs_requested_by ON report_jobs(requested_by);
