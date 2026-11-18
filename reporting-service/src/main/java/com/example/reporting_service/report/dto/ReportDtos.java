package com.example.reporting_service.report.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ReportDtos {

    public record TicketSummaryReport(
            long total, long open, long resolved, Double avgResolutionHours,
            Instant from, Instant to) {
    }

    public record StaffPerformanceRow(
            UUID staffId, long ticketsResolved, Double avgResolutionHours, Double slaComplianceRate) {
    }

    public record StaffPerformanceReport(List<StaffPerformanceRow> rows, Instant from, Instant to) {
    }

    public record CategoryVolumeRow(UUID categoryId, String categoryName, long ticketCount) {
    }

    public record CategoryBreakdownReport(List<CategoryVolumeRow> byCategory, List<CategoryVolumeRow> top3, Instant from, Instant to) {
    }

    public record AssetUtilizationReport(long assigned, long available, long underRepair, long retired) {
    }

    public record SlaBreachRow(UUID categoryId, UUID staffId, long breachCount) {
    }

    public record SlaBreachesReport(long totalBreaches, List<SlaBreachRow> rows, Instant from, Instant to) {
    }
}
