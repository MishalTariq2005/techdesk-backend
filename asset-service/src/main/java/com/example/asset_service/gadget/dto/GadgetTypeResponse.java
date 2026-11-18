package com.example.asset_service.gadget.dto;

import java.time.Instant;
import java.util.UUID;

public record GadgetTypeResponse(
    UUID id,
    String name,
    String description,
    boolean requiresApproval,
    Instant createdAt
) {}
