package com.example.ticket_service.ticket.repository;

import com.example.ticket_service.ticket.entity.TicketAssignmentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketAssignmentLogRepository extends JpaRepository<TicketAssignmentLog, UUID> {
    List<TicketAssignmentLog> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    /** Most recent auto-assignments (actorId null = system) TO this staff member, newest first — used for burst detection. */
    List<TicketAssignmentLog> findTop3ByAssignedToAndActorIdIsNullOrderByCreatedAtDesc(UUID assignedTo);

    /** Latest auto-assignment decision (with score breakdown) for a given ticket — backs GET /api/tickets/{id}/assignment-explanation. */
    Optional<TicketAssignmentLog> findTopByTicketIdAndScoreBreakdownIsNotNullOrderByCreatedAtDesc(UUID ticketId);
}
