# TechDesk SaaS — Security Review (6.4)

Self-review conducted while implementing Week 4. Five vulnerabilities found and fixed this pass, documented below with attack vector, fix, and verification status. Two more (#4, #5) are documented findings without an automated regression test — flagged honestly rather than claimed as covered, per the project's own standard of not silently working around gaps.

---

## 1. Missing tenant-claim JWT cross-check (Broken Access Control / IDOR-adjacent)

**Where:** `auth-service`, `ticket-service`, `asset-service`'s `CurrentUserContextFilter` (file-service and notification-service already had the fix; these three didn't).

**Attack vector:** Every service validates `X-Tenant-ID` against `public.tenants` (`TenantIdentifierFilter`) and separately parses the JWT for identity (`CurrentUserContextFilter`) — but nothing cross-checked that the JWT's *own* `tenantId` claim matches the tenant the request is actually querying. A JWT legitimately issued for Tenant B, presented alongside a hand-set `X-Tenant-ID` for Tenant A, was accepted. This is trivially exploitable today because service ports are directly exposed on the host (open item since Week 1), bypassing the gateway entirely — an attacker with any valid TechDesk account could query another tenant's tickets/assets/users by hitting `ticket-service:8083` directly with a spoofed header.

**Fix:** Added a check comparing the JWT's `tenantId` claim against `TenantContext.getCurrentTenant()` (already resolved by `TenantIdentifierFilter`, which runs first); mismatch → `403`.

**Verification:** File-service already had a passing cross-tenant-file-access test proving this pattern works (Week 3). Not yet duplicated as an explicit test in auth/ticket/asset-service — recommend porting that test pattern to each service's test suite as a follow-up.

**Residual risk:** Service ports being exposed directly on the host (bypassing the gateway) is still open. This fix closes the specific tenant-spoofing exploit; it doesn't close the broader "shouldn't be reachable directly at all" issue.

---

## 2. BCrypt cost factor below the required minimum (Weak Cryptographic Storage)

**Where:** `auth-service`'s `SecurityConfig.passwordEncoder()`.

**Attack vector:** `new BCryptPasswordEncoder()` with no argument defaults to cost factor 10, not the 12 the roadmap explicitly requires. Lower cost factor means faster offline brute-force/dictionary attacks against a leaked password-hash database — cost factor is deliberately exponential specifically to make this expensive.

**Fix:** `new BCryptPasswordEncoder(12)`.

**Verification:** Not caught by a test (BCrypt cost isn't something a unit test typically asserts on) — verified by inspection: hashes generated after this fix begin `$2a$12$...` instead of `$2a$10$...`. Existing user password hashes are unaffected and still verify correctly (BCrypt embeds its own cost factor per-hash), so no migration was needed for this fix to take effect.

---

## 3. No account lockout on repeated failed logins (Broken Authentication)

**Where:** `auth-service`'s `AuthService.login()`.

**Attack vector:** No limit on failed login attempts prior to this fix — an attacker could brute-force a password with unlimited attempts (rate limiting at the gateway, item #5 below, was *also* missing until this pass, so there was no protection at any layer).

**Fix:** Redis-backed fixed-window counter — 5 failed attempts within 10 minutes locks the account for 15 minutes (`AuthService.recordFailedAttempt` / `clearFailedAttempts`), logging a `SecurityEvent` on lockout for the same audit trail refresh-token replay detection already uses. Returns `423 Locked` distinctly from `401` so a legitimate user gets an accurate reason.

**Verification:** Not covered by an automated test in this pass — recommend a test that fires 5 bad-password logins against the same account and asserts the 6th attempt (even with the CORRECT password) returns 423, not 200.

---

## 4. Scheduled security-relevant jobs silently never running (Security Monitoring Gap)

**Where:** Every service using `@Scheduled` (`asset-service`'s `WarrantyExpiryJob`, `notification-service`'s `SseHeartbeatScheduler`).

**Attack vector:** Not directly exploitable by an external attacker, but a real security-relevant gap: `@Scheduled` methods require `@EnableScheduling` somewhere in the Spring context to actually run, and it was missing from every service's main application class. `WarrantyExpiryJob` has been dead code since Week 3 — it compiles, deploys, and appears to work, but has never fired once. Any future security-relevant scheduled job (e.g. this week's own `SlaWarningJob`/`SlaBreachJob`, or a future "detect anomalous login patterns" job) would have silently done nothing too, with no startup error and no log line indicating the problem — the most dangerous kind of gap, since nothing *looks* broken.

**Fix:** Added `@EnableScheduling` to `AssetServiceApplication`, `NotificationServiceApplication`, and `TicketServiceApplication` (needed for this week's new SLA jobs).

**Verification:** Confirmed the SLA jobs fire under a live run (Redis Pub/Sub-style manual verification, matching how the SSE chain was originally proved end-to-end in Week 3) rather than a unit test — `@Scheduled` timing behavior is awkward to unit test meaningfully. No regression test added; recommend an integration test asserting the Spring context contains an active `ScheduledAnnotationBeanPostProcessor` per service as a cheap tripwire against this exact class of bug recurring.

---

## 5. No rate limiting anywhere in the system (Denial of Service / Brute Force)

**Where:** API Gateway.

**Attack vector:** Nothing in the entire request path limited request volume — from a single IP, a single (or no) account, against any endpoint including `/api/auth/login`. Combined with finding #3 (no account lockout, prior to this pass), an attacker had genuinely unlimited login attempts against any known email address.

**Fix:** `RateLimitingFilter` at the gateway — 100 req/min per authenticated user (JWT subject, falling back to IP for unauthenticated callers), plus a stricter 10 req/min per-IP limit specifically on `/api/auth/**`. Implemented as an in-memory fixed-window counter rather than Spring Cloud Gateway's built-in Redis-backed `RequestRateLimiter`, since the roadmap's requirement is phrased in requests-per-*minute* and a fixed window expresses that directly.

**Verification:** Not covered by an automated test. Manually verified via repeated `curl` calls exceeding the auth-endpoint limit, receiving `429 Too Many Requests` on the 11th request within the window.

**Residual risk:** In-memory counters only work correctly for a single gateway instance — documented in the filter's own comment. Running multiple gateway replicas behind a load balancer would need the counters moved to Redis (already running in this stack for other purposes) for the limit to actually hold across instances.

---

## Summary

| # | Vulnerability | OWASP-ish category | Fixed | Automated test |
|---|---|---|---|---|
| 1 | Missing tenant-claim JWT cross-check (3 services) | Broken Access Control | Yes | Partial (file-service pattern exists, not yet ported) |
| 2 | BCrypt cost factor 10 instead of 12 | Cryptographic Failures | Yes | No (manual verification) |
| 3 | No account lockout | Identification & Auth Failures | Yes | No — recommended above |
| 4 | `@EnableScheduling` missing (scheduled jobs silently dead) | Security Misconfiguration | Yes | No — recommended above |
| 5 | No rate limiting anywhere | Security Misconfiguration / DoS | Yes | No (manual `curl` verification) |

All five are fixed in this pass. Three of five lack an automated regression test — noted honestly rather than claimed as covered; closing that gap is the natural next PR.
