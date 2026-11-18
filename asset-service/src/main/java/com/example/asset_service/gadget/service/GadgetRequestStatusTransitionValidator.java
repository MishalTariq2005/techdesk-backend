package com.example.asset_service.gadget.service;

import com.example.asset_service.gadget.exception.InvalidGadgetRequestTransitionException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Same shape as ticket-service's TicketStatusTransitionValidator: a
 * Map<String, Set<String>> of allowed next-states, with anything requiring
 * a role check handled at the controller (@RequiresPermission) rather than
 * folded into this map — unlike tickets' RESOLVED->OPEN special case, every
 * gated transition here (approve/reject) maps to one clean permission
 * (GADGET_APPROVE), so there's no single-endpoint-multiple-roles wrinkle
 * to handle inside the validator itself.
 *
 * APPROVED and READY_FOR_PICKUP are real states that get written and logged,
 * but nothing external ever requests them directly — they're intermediate
 * steps GadgetRequestService walks through atomically inside one approve()
 * call (see the roadmap's own "status → APPROVED → READY_FOR_PICKUP →
 * ASSIGNED" as a single arrow, not three separate triggered actions).
 */
@Component
public class GadgetRequestStatusTransitionValidator {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
        "REQUESTED",               Set.of("PENDING_MANAGER_APPROVAL", "APPROVED", "IN_PROCUREMENT", "CANCELLED"),
        "PENDING_MANAGER_APPROVAL", Set.of("APPROVED", "IN_PROCUREMENT", "REJECTED", "CANCELLED"),
        "APPROVED",                Set.of("READY_FOR_PICKUP"),
        "READY_FOR_PICKUP",        Set.of("ASSIGNED"),
        // IN_PROCUREMENT deliberately has no outbound edge to itself — a
        // failed retry-procurement attempt (still no asset available) is
        // logged as an event but does NOT re-enter this map as a "transition
        // to the same state"; see GadgetRequestService.retryProcurement().
        "IN_PROCUREMENT",          Set.of("APPROVED"),
        "ASSIGNED",                Set.of(), // terminal
        "REJECTED",                Set.of(), // terminal
        "CANCELLED",               Set.of()  // terminal
    );

    private static final Set<String> VALID_STATUSES = ALLOWED_TRANSITIONS.keySet();

    public void validate(String from, String to) {
        if (!VALID_STATUSES.contains(to)) {
            throw new InvalidGadgetRequestTransitionException("Unknown status: " + to);
        }
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new InvalidGadgetRequestTransitionException(
                "Invalid transition " + from + " -> " + to + ". Allowed from " + from + ": " + allowed);
        }
    }
}
