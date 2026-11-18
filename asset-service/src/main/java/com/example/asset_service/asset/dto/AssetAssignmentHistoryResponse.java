package com.example.asset_service.asset.dto;

import com.example.asset_service.asset.entity.AssetAssignmentEventType;

import java.time.Instant;
import java.util.UUID;

public record AssetAssignmentHistoryResponse(
    UUID id,
    UUID employeeId,
    AssetAssignmentEventType eventType,
    Instant eventTimestamp,
    UUID actorUserId,
    String notes
) {}
