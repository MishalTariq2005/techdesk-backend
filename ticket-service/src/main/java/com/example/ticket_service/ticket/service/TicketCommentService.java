package com.example.ticket_service.ticket.service;

import com.example.ticket_service.client.NotificationClient;
import com.example.ticket_service.rbac.security.CurrentUserContext;
import com.example.ticket_service.ticket.dto.CommentResponse;
import com.example.ticket_service.ticket.dto.CreateCommentRequest;
import com.example.ticket_service.ticket.entity.Ticket;
import com.example.ticket_service.ticket.entity.TicketComment;
import com.example.ticket_service.ticket.repository.TicketCommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TicketCommentService {

    private final TicketCommentRepository commentRepository;
    private final TicketService ticketService; // reused for visibility check, not duplicated here
    private final NotificationClient notificationClient;

    public TicketCommentService(TicketCommentRepository commentRepository, TicketService ticketService,
                                 NotificationClient notificationClient) {
        this.commentRepository = commentRepository;
        this.ticketService = ticketService;
        this.notificationClient = notificationClient;
    }

    @Transactional
    public CommentResponse addComment(UUID ticketId, CreateCommentRequest request) {
        Ticket ticket = ticketService.getAndCheckVisibility(ticketId); // 404 if caller can't see this ticket
        CurrentUserContext.Identity caller = CurrentUserContext.get();

        // Only IT staff/manager/admin (holders of TICKET_RESOLVE) can mark a
        // comment internal. An employee's comment is always public — even
        // if they somehow set internal=true in the request body, force it
        // false rather than reject the whole request outright.
        boolean canMarkInternal = caller != null && caller.permissions().contains("TICKET_RESOLVE");
        boolean effectiveInternal = request.internal() && canMarkInternal;

        TicketComment comment = new TicketComment(
            ticketId, UUID.fromString(caller.userId()), request.content(), effectiveInternal);
        comment = commentRepository.save(comment);

        notifyOtherParty(ticket, UUID.fromString(caller.userId()), effectiveInternal);

        return toResponse(comment);
    }

    /**
     * 5.4 trigger — notifies whichever of {creator, assignee} did NOT
     * write this comment. Assignee is checked first and always notified
     * regardless of internal (IT staff/manager/admin can see internal
     * notes) — this also correctly covers the edge case where the creator
     * happens to BE the assignee (e.g. IT staff filing a ticket that later
     * gets auto-assigned to themselves): they get exactly one notification,
     * via the assignee branch, not zero. The creator is only notified
     * separately when they're NOT also the assignee (already covered
     * above) and the comment isn't internal — an internal note isn't
     * visible to a plain employee creator (see listVisibleComments' own
     * filter), so notifying them about something they can't open would be
     * a confusing dead end.
     */
    private void notifyOtherParty(Ticket ticket, UUID authorId, boolean isInternal) {
        UUID creator = ticket.getCreatedBy();
        UUID assignee = ticket.getAssignedTo();
        boolean creatorIsAlsoAssignee = assignee != null && assignee.equals(creator);

        if (assignee != null && !assignee.equals(authorId)) {
            notificationClient.notify(assignee, "COMMENT_ADDED",
                "New comment on an assigned ticket", "A new comment was added to \"" + ticket.getTitle() + "\".",
                "TICKET", ticket.getId());
        }
        if (!creatorIsAlsoAssignee && !isInternal && !creator.equals(authorId)) {
            notificationClient.notify(creator, "COMMENT_ADDED",
                "New comment on your ticket", "A new comment was added to \"" + ticket.getTitle() + "\".",
                "TICKET", ticket.getId());
        }
    }

    /** Filters out internal notes for callers who aren't IT staff/manager/admin. */
    public List<CommentResponse> listVisibleComments(UUID ticketId) {
        CurrentUserContext.Identity caller = CurrentUserContext.get();
        boolean canSeeInternal = caller != null && caller.permissions().contains("TICKET_RESOLVE");

        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
            .filter(c -> canSeeInternal || !c.isInternal())
            .map(this::toResponse)
            .toList();
    }

    private CommentResponse toResponse(TicketComment c) {
        return new CommentResponse(c.getId(), c.getAuthorId(), c.getContent(), c.isInternal(), c.getCreatedAt());
    }
}