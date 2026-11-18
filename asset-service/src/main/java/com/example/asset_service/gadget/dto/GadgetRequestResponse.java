package com.example.asset_service.gadget.dto;

import java.time.Instant;
import java.util.UUID;

public record GadgetRequestResponse(
    UUID id,
    UUID gadgetTypeId,
    String gadgetTypeName,
    UUID employeeId,
    String reason,
    String status,
    UUID assignedAssetId,
    String rejectionReason,
    Instant createdAt,
    Instant updatedAt
) {}
