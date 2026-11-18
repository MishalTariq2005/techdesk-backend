package com.example.ticket_service.ticket.exception;

/**
 * Thrown when Hibernate's OptimisticLockException fires on a status update —
 * see Ticket.version. Carries the CURRENT state of the ticket (re-read after
 * the conflict) so the 409 response can show the client what actually
 * changed, not just "conflict, try again blind."
 */
public class TicketConflictException extends RuntimeException {
    private final Object currentState;

    public TicketConflictException(String message, Object currentState) {
        super(message);
        this.currentState = currentState;
    }

    public Object getCurrentState() {
        return currentState;
    }
}
