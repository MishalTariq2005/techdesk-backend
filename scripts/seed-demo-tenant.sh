#!/usr/bin/env bash
# 6.5: "Write a seed script that bootstraps a demo tenant (tenant_demo) with
# 10 employees, 3 IT staff, 20 tickets in various states."
#
# WHY THIS GOES THROUGH RAW SQL, NOT THE REST API: this project has no real
# SUPER_ADMIN bootstrap flow yet (open item #7 since Week 1 — there is no
# account that can call POST /api/tenants to create a tenant through the
# actual API). The established workaround elsewhere in this codebase for
# exactly this gap was direct SQL against newly-created tenant schemas
# (e.g. `newadmin@acme.test` in Week 1) — this script follows the same
# precedent rather than pretending a bootstrap flow exists.
#
# Usage:
#   ./scripts/seed-demo-tenant.sh
# Requires: psql on PATH, postgres reachable at localhost:5432 (i.e. run
# this after `docker-compose up -d postgres` at minimum — no other service
# needs to be running for this script itself, though you'll want auth-service
# etc. up to actually log in as the seeded users afterward).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
MIGRATIONS_DIR="$REPO_ROOT/tenant-service/src/main/resources/db/migration/tenant"

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-techdesk_admin}"
PGDATABASE="${PGDATABASE:-techdesk}"
export PGPASSWORD="${PGPASSWORD:-changeme}"

SCHEMA="tenant_demo"
PSQL="psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -v ON_ERROR_STOP=1"

echo "==> Registering $SCHEMA in public.tenants (idempotent)"
$PSQL -c "
INSERT INTO public.tenants (name, schema_name, status)
VALUES ('Demo Company', '$SCHEMA', 'ACTIVE')
ON CONFLICT (schema_name) DO NOTHING;
"

echo "==> Creating schema $SCHEMA (idempotent)"
$PSQL -c "CREATE SCHEMA IF NOT EXISTS \"$SCHEMA\";"

echo "==> Applying tenant-service's migration set against $SCHEMA"
echo "    (mirrors what SchemaProvisioningService does programmatically for a real tenant —"
echo "    tracked via its own flyway_schema_history so re-running this script is safe/idempotent)"
for migration in "$MIGRATIONS_DIR"/V*.sql; do
    filename=$(basename "$migration")
    already_applied=$($PSQL -tA -c "
        SELECT EXISTS (
            SELECT 1 FROM \"$SCHEMA\".flyway_schema_history
            WHERE script = '$filename'
        );" 2>/dev/null || echo "f")

    if [ "$already_applied" = "t" ]; then
        echo "    skip (already applied): $filename"
        continue
    fi

    echo "    applying: $filename"
    $PSQL -v ON_ERROR_STOP=1 -c "SET search_path TO \"$SCHEMA\", public;" -f "$migration"
done

# Bootstrap a minimal flyway_schema_history if this is the first run (so the
# idempotency check above has something to query on a re-run). Real
# tenant-service provisioning does this via the actual Flyway Java API;
# this script fakes just enough of it to be safely re-runnable.
$PSQL -c "
CREATE TABLE IF NOT EXISTS \"$SCHEMA\".flyway_schema_history (script VARCHAR(255) PRIMARY KEY);
" >/dev/null
for migration in "$MIGRATIONS_DIR"/V*.sql; do
    filename=$(basename "$migration")
    $PSQL -c "INSERT INTO \"$SCHEMA\".flyway_schema_history (script) VALUES ('$filename') ON CONFLICT DO NOTHING;" >/dev/null
done

echo "==> Seeding demo users, staff profiles, and tickets"
$PSQL -v ON_ERROR_STOP=1 -c "SET search_path TO \"$SCHEMA\", public;" -f "$SCRIPT_DIR/seed-demo-data.sql"

echo "==> Done. Demo tenant '$SCHEMA' is ready."
echo "    Log in with X-Tenant-ID: $SCHEMA"
echo "    Company admin: admin@demo.techdesk.local / Demo1234!"
echo "    (all seeded users share this password — see seed-demo-data.sql)"
