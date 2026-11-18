-- V9: 4.4 — auto-assignment support tables.
-- staff_profiles / staff_skills are managed via ticket-service's own API
-- (see StaffProfileController), NOT synced automatically from auth-service
-- user creation — no message bus exists yet to make "seeded during user
-- creation" (as the roadmap literally says) a real cross-service event.
-- An IT_MANAGER/COMPANY_ADMIN sets these up explicitly after inviting an
-- IT_STAFF user. Documented as a deliberate scope decision.

CREATE TABLE staff_profiles (
    staff_user_id       UUID PRIMARY KEY, -- no FK: users live in auth-service's schema, different service
    available           BOOLEAN NOT NULL DEFAULT TRUE,
    location            VARCHAR(100), -- matched against ticket.reporter_department for locationMatch scoring
    max_active_tickets  INTEGER NOT NULL DEFAULT 15,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE staff_skills (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_user_id  UUID NOT NULL REFERENCES staff_profiles(staff_user_id) ON DELETE CASCADE,
    category_id    UUID NOT NULL REFERENCES ticket_categories(id) ON DELETE CASCADE,
    skill_level    INTEGER NOT NULL CHECK (skill_level BETWEEN 1 AND 5),
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (staff_user_id, category_id)
);

CREATE INDEX idx_staff_skills_category ON staff_skills(category_id);

-- The existing `reason` column (VARCHAR(500)) isn't big enough for a full
-- JSON score breakdown across up to ~10 candidates. Separate column so the
-- short human-readable `reason` and the full machine-readable breakdown
-- don't fight over the same field.
ALTER TABLE ticket_assignment_logs ADD COLUMN score_breakdown TEXT;
