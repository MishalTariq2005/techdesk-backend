# TechDesk SaaS

Multi-tenant IT Service Management platform. Spring Boot microservices behind a Spring Cloud Gateway, schema-per-tenant PostgreSQL, React frontend (not built this pass — see "What's not built" below).

## Services

| Service | Port | Purpose |
|---|---|---|
| eureka-server | 8761 | Service discovery |
| api-gateway | 8080 | Single entry point — tenant resolution, JWT role check, rate limiting, security headers |
| auth-service | 8081 | Login/refresh/logout, password reset, user invitations, RBAC user management |
| tenant-service | 8082 | Tenant provisioning, owns all Flyway migrations (public + per-tenant) |
| ticket-service | 8083 | Tickets, categories, priority engine, auto-assignment, SLA engine |
| asset-service | 8084 | Assets, gadget requests |
| file-service | 8085 | File attachments |
| notification-service | 8086 | Email + real-time (SSE) notifications |
| audit-log-service | 8087 | Tamper-evident audit trail (HMAC-signed, immutable) |
| reporting-service | 8088 | Analytics reports, async job pattern for slow queries |

Plus: `postgres` (5432), `pgadmin` (5050), `redis` (6379), `mailhog` (1025 SMTP / 8025 web UI).

## Prerequisites

- Docker + Docker Compose
- JDK 17 (for running `mvn` locally outside Docker)
- `psql` on PATH (for the seed script)

## Environment variables

Local development works out of the box with the dev defaults baked into each service's `application.yml` (all clearly commented `# dev only`). For anything resembling production, set these and use the prod compose overlay (see below):

| Variable | Used by | Purpose |
|---|---|---|
| `POSTGRES_PASSWORD` | postgres, every service's datasource | Database password |
| `PGADMIN_PASSWORD` | pgadmin | pgAdmin login |
| `JWT_SECRET` | every service | Shared HMAC signing key for JWTs — **must be identical across every service** |
| `AUDIT_HMAC_SECRET` | audit-log-service | HMAC key for audit log tamper-detection signatures |
| `FRONTEND_BASE_URL` | auth-service | Base URL used in password-reset/invite email links (defaults to `http://localhost:5173`) |
| `SLA_ZONE_ID` | ticket-service | Timezone business-hours clock times are interpreted in (defaults to `UTC`) |

## Running locally

```bash
docker-compose up -d
```

First boot takes a few minutes (each service builds its own Maven image). Check status:

```bash
docker-compose ps
curl http://localhost:8080/actuator/health   # gateway
curl http://localhost:8761                    # Eureka dashboard
```

MailHog's web UI (`http://localhost:8025`) is where password-reset and invite emails actually show up in dev — nothing sends real email locally.

### Production profile

```bash
export POSTGRES_PASSWORD=... PGADMIN_PASSWORD=... JWT_SECRET=... AUDIT_HMAC_SECRET=... FRONTEND_BASE_URL=https://your-domain.example
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

`docker-compose.prod.yml` is an overlay, not a replacement — it only swaps hardcoded dev credentials for required env vars (missing one fails `docker-compose up` immediately rather than silently falling back to a dev secret).

## Running tests

Per-service, since there's no reactor parent pom tying them together:

```bash
cd ticket-service && mvn test
```

Or every service (matches what CI runs):

```bash
for svc in eureka-server api-gateway auth-service tenant-service ticket-service asset-service file-service notification-service audit-log-service reporting-service; do
  (cd "$svc" && mvn -B verify) || echo "FAILED: $svc"
done
```

## Seeding demo data

```bash
docker-compose up -d postgres
./scripts/seed-demo-tenant.sh
```

Provisions a `tenant_demo` schema with 1 company admin, 1 IT manager, 3 IT staff, 10 employees, and 20 tickets in various states. All seeded users share the password `Demo1234!`. See the script's own header comment for why this goes through raw SQL rather than the REST API (no SUPER_ADMIN bootstrap flow exists yet — a known open item, not an oversight).

Log in against auth-service with header `X-Tenant-ID: tenant_demo` and one of:
- `admin@demo.techdesk.local` (COMPANY_ADMIN)
- `manager@demo.techdesk.local` (IT_MANAGER)
- `itstaff1@demo.techdesk.local` / `itstaff2@demo.techdesk.local` / `itstaff3@demo.techdesk.local` (IT_STAFF)
- `employee1@demo.techdesk.local` through `employee10@demo.techdesk.local` (EMPLOYEE)

## Migrations

All Flyway migrations — both the shared `public` schema and every per-tenant schema — live under `tenant-service/src/main/resources/db/migration/`, applied programmatically to each tenant schema by `SchemaProvisioningService`. **A migration file existing in the repo does not mean it's been applied to an already-provisioned tenant** — tenant-service only runs new migrations against a tenant schema when that schema is (re-)provisioned. After pulling changes that add a tenant-schema migration (e.g. this week's `V14`/`V15` for SLA + reporting), apply it to existing tenant schemas manually, the same way `newadmin@acme.test`'s stale-permission bug and other pre-4.0 tenants were handled.

`audit-log-service` is the one exception — it owns migrations against the shared `public` schema independently, tracked via its own Flyway history table (`flyway_schema_history_audit_log_service`) so it doesn't collide with tenant-service's migration history on the same physical schema. See its `application.yml` for the full reasoning.

## Known open items

See `SECURITY_REVIEW.md` for the security-specific list. Broader open items carried across all four weeks (RLS bypassed by superuser, ports exposed directly bypassing the gateway, no real SUPER_ADMIN bootstrap flow, stale denormalized permission snapshots on directly-SQL-created users, and others) are documented inline in the relevant service's code comments rather than duplicated here.

## What's not built this pass

- **React frontend (5.5)** — explicitly out of scope for this session; the roadmap's frontend tasks (Vite/React/Tailwind setup, three portals, SSE notification bell, optimistic UI) remain undone.
- **Full at-least-once notification delivery / chaos test (6.5's own challenge)** — `NotificationClient` is still fire-and-forget with no retry queue. A real outbox-and-retry mechanism (and the chaos test that shuts down notification-service mid-workflow to prove it) is the natural next piece of work.
- **Async report generation retention** — `report_jobs` rows accumulate with no cleanup job; fine for demo/dev use, would need a retention policy for real production use.
