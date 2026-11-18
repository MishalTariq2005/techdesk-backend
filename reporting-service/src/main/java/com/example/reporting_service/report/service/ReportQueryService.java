package com.example.reporting_service.report.service;

import com.example.reporting_service.report.dto.ReportDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Reads directly against the tenant's own Postgres schema via plain
 * JdbcTemplate rather than calling ticket-service/asset-service over the
 * network — a deliberate architecture decision, not an oversight. All of a
 * tenant's tables (tickets, assets, ticket_categories, ...) live in ONE
 * physical schema regardless of which microservice owns the JPA entity for
 * it; a reporting service that only needs READ access can query across
 * those tables directly, the same way asset-service's TenantLookupService
 * already reads public.tenants directly instead of calling tenant-service.
 * This avoids fan-out HTTP calls to 6 services for every report and keeps
 * "performant on 100,000+ ticket records" (roadmap's own requirement)
 * realistic — Hibernate/JPA N+1 traversal across service boundaries would
 * not be.
 *
 * IMPORTANT: JdbcTemplate's connections come straight from the pooled
 * DataSource, NOT through Hibernate's SchemaMultiTenantConnectionProvider —
 * that only intercepts Hibernate sessions. So every query here explicitly
 * schema-qualifies table names via qualify() rather than relying on
 * search_path being set. tenantSchema is validated (alphanumeric/underscore
 * only) before being interpolated into SQL, since it's the one piece of
 * this class's input that isn't a bind parameter — defense in depth even
 * though the value already passed through TenantIdentifierFilter's
 * public.tenants lookup upstream.
 *
 * FIX: java.time.Instant passed directly as a JDBC bind parameter fails
 * against this Postgres driver version with "Can't infer the SQL type to
 * use for an instance of java.time.Instant" — the driver needs an explicit
 * java.sql type, not a java.time one, at the raw JDBC boundary (this
 * limitation doesn't apply to JPA/Hibernate, which handles Instant fine via
 * its own type system — it's specific to plain JdbcTemplate calls). ts()
 * converts at the last possible moment, right where each Instant actually
 * gets bound to a PreparedStatement parameter.
 */
@Service
public class ReportQueryService {

    private static final Pattern SAFE_SCHEMA = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final List<String> RESOLVED_STATUSES = List.of("RESOLVED", "CLOSED");
    private static final List<String> TERMINAL_STATUSES = List.of("RESOLVED", "CLOSED", "CANCELLED");

    private final JdbcTemplate jdbcTemplate;

    public ReportQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TicketSummaryReport ticketSummary(String tenantSchema, Instant from, Instant to) {
        String tickets = qualify(tenantSchema, "tickets");

        long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + tickets + " WHERE created_at BETWEEN ? AND ?",
                Long.class, ts(from), ts(to));

        long open = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + tickets + " WHERE created_at BETWEEN ? AND ? AND status NOT IN (?,?,?)",
                Long.class, ts(from), ts(to), TERMINAL_STATUSES.get(0), TERMINAL_STATUSES.get(1), TERMINAL_STATUSES.get(2));

        long resolved = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + tickets + " WHERE created_at BETWEEN ? AND ? AND status IN (?,?)",
                Long.class, ts(from), ts(to), RESOLVED_STATUSES.get(0), RESOLVED_STATUSES.get(1));

        // Approximation, documented: there is no dedicated resolved_at column
        // on tickets, so resolution time is measured as updated_at - created_at
        // for tickets currently in a resolved-family status. Accurate whenever
        // RESOLVED/CLOSED was the ticket's last status transition (the common
        // case); would overstate resolution time for a ticket resolved,
        // reopened, then resolved again, since updated_at reflects the LATEST
        // change, not necessarily the first resolution.
        Double avgResolutionHours = jdbcTemplate.queryForObject(
                "SELECT AVG(EXTRACT(EPOCH FROM (updated_at - created_at)) / 3600.0) FROM " + tickets
                    + " WHERE created_at BETWEEN ? AND ? AND status IN (?,?)",
                Double.class, ts(from), ts(to), RESOLVED_STATUSES.get(0), RESOLVED_STATUSES.get(1));

        return new TicketSummaryReport(total, open, resolved, avgResolutionHours, from, to);
    }

    public StaffPerformanceReport staffPerformance(String tenantSchema, Instant from, Instant to) {
        String tickets = qualify(tenantSchema, "tickets");

        String sql = "SELECT assigned_to, "
            + "count(*) AS resolved_count, "
            + "AVG(EXTRACT(EPOCH FROM (updated_at - created_at)) / 3600.0) AS avg_hours, "
            + "AVG(CASE WHEN sla_breached THEN 0.0 ELSE 1.0 END) AS compliance_rate "
            + "FROM " + tickets + " "
            + "WHERE assigned_to IS NOT NULL AND created_at BETWEEN ? AND ? AND status IN (?,?) "
            + "GROUP BY assigned_to";

        List<StaffPerformanceRow> rows = jdbcTemplate.query(sql,
                (rs, rowNum) -> new StaffPerformanceRow(
                        UUID.fromString(rs.getString("assigned_to")),
                        rs.getLong("resolved_count"),
                        (Double) rs.getObject("avg_hours"),
                        (Double) rs.getObject("compliance_rate")),
                ts(from), ts(to), RESOLVED_STATUSES.get(0), RESOLVED_STATUSES.get(1));

        return new StaffPerformanceReport(rows, from, to);
    }

    public CategoryBreakdownReport categoryBreakdown(String tenantSchema, Instant from, Instant to) {
        String tickets = qualify(tenantSchema, "tickets");
        String categories = qualify(tenantSchema, "ticket_categories");

        String sql = "SELECT t.category_id, c.name, count(*) AS ticket_count "
            + "FROM " + tickets + " t JOIN " + categories + " c ON c.id = t.category_id "
            + "WHERE t.created_at BETWEEN ? AND ? "
            + "GROUP BY t.category_id, c.name "
            + "ORDER BY ticket_count DESC";

        List<CategoryVolumeRow> all = jdbcTemplate.query(sql,
                (rs, rowNum) -> new CategoryVolumeRow(
                        UUID.fromString(rs.getString("category_id")), rs.getString("name"), rs.getLong("ticket_count")),
                ts(from), ts(to));

        List<CategoryVolumeRow> top3 = all.stream().limit(3).toList();
        return new CategoryBreakdownReport(all, top3, from, to);
    }

    public AssetUtilizationReport assetUtilization(String tenantSchema) {
        String assets = qualify(tenantSchema, "assets");

        String sql = "SELECT status, count(*) AS cnt FROM " + assets
            + " WHERE deleted_at IS NULL GROUP BY status";

        Map<String, Long> counts = new java.util.HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            counts.put(rs.getString("status"), rs.getLong("cnt"));
        });

        return new AssetUtilizationReport(
                counts.getOrDefault("ASSIGNED", 0L),
                counts.getOrDefault("AVAILABLE", 0L),
                counts.getOrDefault("UNDER_REPAIR", 0L),
                counts.getOrDefault("RETIRED", 0L));
    }

    public SlaBreachesReport slaBreaches(String tenantSchema, Instant from, Instant to) {
        String tickets = qualify(tenantSchema, "tickets");

        String sql = "SELECT category_id, assigned_to, count(*) AS breach_count FROM " + tickets
            + " WHERE sla_breached = true AND created_at BETWEEN ? AND ? "
            + "GROUP BY category_id, assigned_to";

        List<SlaBreachRow> rows = jdbcTemplate.query(sql,
                (rs, rowNum) -> new SlaBreachRow(
                        UUID.fromString(rs.getString("category_id")),
                        rs.getString("assigned_to") != null ? UUID.fromString(rs.getString("assigned_to")) : null,
                        rs.getLong("breach_count")),
                ts(from), ts(to));

        long total = rows.stream().mapToLong(SlaBreachRow::breachCount).sum();
        return new SlaBreachesReport(total, rows, from, to);
    }

    private Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private String qualify(String tenantSchema, String table) {
        if (!SAFE_SCHEMA.matcher(tenantSchema).matches()) {
            throw new IllegalArgumentException("Unsafe tenant schema name: " + tenantSchema);
        }
        return "\"" + tenantSchema + "\".\"" + table + "\"";
    }
}