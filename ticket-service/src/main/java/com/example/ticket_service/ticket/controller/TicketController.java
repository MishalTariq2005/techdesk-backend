package com.example.ticket_service.ticket.controller;

import com.example.ticket_service.assignment.dto.AssignmentExplanationResponse;
import com.example.ticket_service.assignment.dto.CandidateScoreDto;
import com.example.ticket_service.rbac.annotation.RequiresPermission;
import com.example.ticket_service.ticket.dto.*;
import com.example.ticket_service.ticket.entity.Ticket;
import com.example.ticket_service.ticket.entity.TicketAssignmentLog;
import com.example.ticket_service.ticket.repository.TicketAssignmentLogRepository;
import com.example.ticket_service.ticket.service.TicketCommentService;
import com.example.ticket_service.ticket.service.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final TicketCommentService commentService;
    private final TicketAssignmentLogRepository logRepository;
    private final ObjectMapper objectMapper;

    public TicketController(TicketService ticketService, TicketCommentService commentService,
                             TicketAssignmentLogRepository logRepository, ObjectMapper objectMapper) {
        this.ticketService = ticketService;
        this.commentService = commentService;
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @RequiresPermission("TICKET_CREATE")
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        return ResponseEntity.ok(ticketService.create(request));
    }

    @GetMapping
    @RequiresPermission({"TICKET_VIEW_ALL", "TICKET_VIEW_OWN"})
    public ResponseEntity<PagedTicketResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ticketService.list(status, priority, categoryId, assignedTo, page, size));
    }

    @GetMapping("/{id}")
    @RequiresPermission({"TICKET_VIEW_ALL", "TICKET_VIEW_OWN"})
    public ResponseEntity<TicketDetailResponse> getDetail(@PathVariable UUID id) {
        Ticket ticket = ticketService.getAndCheckVisibility(id);

        List<CommentResponse> comments = commentService.listVisibleComments(id);
        List<AssignmentLogResponse> history = logRepository.findByTicketIdOrderByCreatedAtAsc(id).stream()
            .map(this::toLogResponse)
            .toList();

        return ResponseEntity.ok(new TicketDetailResponse(ticketService.toResponse(ticket), comments, history));
    }

    @PatchMapping("/{id}/status")
    @RequiresPermission("TICKET_RESOLVE")
    public ResponseEntity<TicketResponse> updateStatus(@PathVariable UUID id,
                                                        @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(ticketService.updateStatus(id, request));
    }

    @PostMapping("/{id}/comments")
    @RequiresPermission({"TICKET_VIEW_ALL", "TICKET_VIEW_OWN"}) // anyone who can see the ticket can comment
    public ResponseEntity<CommentResponse> addComment(@PathVariable UUID id,
                                                        @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.ok(commentService.addComment(id, request));
    }

    /**
     * 4.4: returns the full scoring breakdown for how this ticket got
     * (or didn't get) auto-assigned. Same visibility rule as the ticket
     * itself — checked via getAndCheckVisibility, so this can't be used to
     * probe a ticket you're not otherwise allowed to see.
     */
    @GetMapping("/{id}/assignment-explanation")
    @RequiresPermission({"TICKET_VIEW_ALL", "TICKET_VIEW_OWN"})
    public ResponseEntity<AssignmentExplanationResponse> getAssignmentExplanation(@PathVariable UUID id) {
        Ticket ticket = ticketService.getAndCheckVisibility(id);

        TicketAssignmentLog log = logRepository.findTopByTicketIdAndScoreBreakdownIsNotNullOrderByCreatedAtDesc(id)
            .orElse(null);

        if (log == null) {
            return ResponseEntity.ok(new AssignmentExplanationResponse(
                id, ticket.getAssignedTo(), List.of(), "No auto-assignment decision has been recorded for this ticket yet"));
        }

        try {
            List<CandidateScoreDto> candidates = objectMapper.readValue(
                log.getScoreBreakdown(), objectMapper.getTypeFactory().constructCollectionType(List.class, CandidateScoreDto.class));
            return ResponseEntity.ok(new AssignmentExplanationResponse(id, ticket.getAssignedTo(), candidates, log.getReason()));
        } catch (Exception e) {
            return ResponseEntity.ok(new AssignmentExplanationResponse(
                id, ticket.getAssignedTo(), List.of(), "Score breakdown stored but could not be parsed: " + e.getMessage()));
        }
    }

    private AssignmentLogResponse toLogResponse(TicketAssignmentLog l) {
        return new AssignmentLogResponse(l.getId(), l.getActorId(), l.getFromStatus(),
            l.getToStatus(), l.getAssignedTo(), l.getReason(), l.getCreatedAt());
    }
}
