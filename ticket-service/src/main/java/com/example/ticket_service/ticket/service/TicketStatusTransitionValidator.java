package com.example.ticket_service.ticket.service;

import com.example.ticket_service.ticket.exception.InvalidStatusTransitionException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Encodes the ticket lifecycle from the Project Understanding doc's status
 * table (section 3.3), plus the roadmap's explicit special case: RESOLVED ->
 * OPEN is normally invalid (not even in the map below) but IT_MANAGER can
 * force it — checked separately in TicketService, not folded into the map,
 * so the override is visible as a distinct code path rather than buried in
 * data.
 */
@Component
public class TicketStatusTransitionValidator {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
        "OPEN",                 Set.of("AUTO_ASSIGNED", "CANCELLED"),
        "AUTO_ASSIGNED",        Set.of("IN_PROGRESS", "CANCELLED"),
        "IN_PROGRESS",          Set.of("WAITING_FOR_EMPLOYEE", "WAITING_FOR_APPROVAL", "ESCALATED", "RESOLVED", "CANCELLED"),
        "WAITING_FOR_EMPLOYEE", Set.of("IN_PROGRESS", "RESOLVED"),
        "WAITING_FOR_APPROVAL", Set.of("IN_PROGRESS", "RESOLVED"),
        "ESCALATED",            Set.of("IN_PROGRESS", "RESOLVED"),
        "RESOLVED",             Set.of("CLOSED", "REOPENED"),
        "REOPENED",             Set.of("IN_PROGRESS", "AUTO_ASSIGNED"),
        "CLOSED",               Set.of(), // terminal
        "CANCELLED",            Set.of()  // terminal
    );

    private static final Set<String> VALID_STATUSES = ALLOWED_TRANSITIONS.keySet();

    public void validate(String from, String to, String callerRole) {
        if (!VALID_STATUSES.contains(to)) {
            throw new InvalidStatusTransitionException("Unknown status: " + to);
        }

        // Explicit override, called out by name in the roadmap — IT_MANAGER
        // can force a resolved ticket back open (e.g. employee reports the
        // "fix" didn't actually work, before it even reached CLOSED).
        if ("RESOLVED".equals(from) && "OPEN".equals(to)) {
            if ("IT_MANAGER".equals(callerRole)) {
                return;
            }
            throw new InvalidStatusTransitionException(
                "RESOLVED -> OPEN requires IT_MANAGER role (caller role: " + callerRole + ")");
        }

        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new InvalidStatusTransitionException(
                "Invalid transition " + from + " -> " + to + ". Allowed from " + from + ": " + allowed);
        }
    }
}
