package com.example.ticket_service.sla.controller;

import com.example.ticket_service.rbac.annotation.RequiresPermission;
import com.example.ticket_service.sla.dto.SlaDashboardResponse;
import com.example.ticket_service.ticket.repository.TicketRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * "IT Manager dashboard must show SLA breach count by category and staff
 * member" (6.1). Gated on TICKET_VIEW_ALL rather than a new permission —
 * that's already exactly IT_MANAGER + COMPANY_ADMIN's existing scope, no
 * reason to introduce a redundant permission for the same audience.
 * Reporting-service's GET /api/reports/sla-breaches (6.2) covers the same
 * data with date-range filtering; this endpoint is the always-current,
 * no-params dashboard summary the roadmap separately calls out under 6.1.
 */
@RestController
@RequestMapping("/api/tickets/sla-dashboard")
public class SlaDashboardController {

    private final TicketRepository ticketRepository;

    public SlaDashboardController(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @GetMapping
    @RequiresPermission("TICKET_VIEW_ALL")
    public ResponseEntity<SlaDashboardResponse> getDashboard() {
        List<Object[]> byCategory = ticketRepository.countBreachesByCategory();
        List<Object[]> byStaff = ticketRepository.countBreachesByStaff();

        List<SlaDashboardResponse.CategoryBreachCount> categoryCounts = byCategory.stream()
                .map(row -> new SlaDashboardResponse.CategoryBreachCount((UUID) row[0], (Long) row[1]))
                .toList();

        List<SlaDashboardResponse.StaffBreachCount> staffCounts = byStaff.stream()
                .map(row -> new SlaDashboardResponse.StaffBreachCount((UUID) row[0], (Long) row[1]))
                .toList();

        long total = categoryCounts.stream().mapToLong(SlaDashboardResponse.CategoryBreachCount::breachCount).sum();

        return ResponseEntity.ok(new SlaDashboardResponse(total, categoryCounts, staffCounts));
    }
}
