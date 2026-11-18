package com.example.ticket_service.ticket.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(UUID id, UUID authorId, String content, boolean internal, Instant createdAt) {}
