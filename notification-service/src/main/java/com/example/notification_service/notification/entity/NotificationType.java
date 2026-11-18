package com.example.notification_service.notification.entity;

public enum NotificationType {
    TICKET_CREATED,
    TICKET_ASSIGNED,
    TICKET_RESOLVED,
    COMMENT_ADDED,
    GADGET_REQUEST_APPROVED,
    GADGET_REQUEST_REJECTED,
    // NEW — added to support notifying managers/admins that a gadget
    // request is awaiting their decision (a real gap found during Week 4
    // testing: only approve/reject OUTCOMES notified the employee, nobody
    // in a decision-making role was ever told something needed action).
    GADGET_REQUEST_PENDING_APPROVAL,
    // Defined now so the SLA engine (6.1, not yet built) has a target to
    // call into later — there is no trigger wired up for this yet anywhere
    // in this codebase. Not a functional gap in 5.4; just honestly unwired
    // until 6.1 exists.
    SLA_BREACH_WARNING
}