package com.example.ticket_service.ticket.repository;

import com.example.ticket_service.ticket.entity.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {
    List<TicketComment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
