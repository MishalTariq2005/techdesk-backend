package com.example.ticket_service.sla.dto;

import java.util.List;
import java.util.UUID;

public record SlaDashboardResponse(
        long totalBreached,
        List<CategoryBreachCount> byCategory,
        List<StaffBreachCount> byStaff
) {
    public record CategoryBreachCount(UUID categoryId, long breachCount) {
    }

    public record StaffBreachCount(UUID staffId, long breachCount) {
    }
}
