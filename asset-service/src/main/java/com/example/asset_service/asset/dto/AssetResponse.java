package com.example.asset_service.asset.dto;

import com.example.asset_service.asset.entity.AssetStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * history is populated on GET /api/assets/{id} (full detail) and left empty
 * on the paginated list view — same "detail embeds history, list doesn't"
 * shape as ticket-service's ticket detail vs. ticket list.
 */
public record AssetResponse(
    UUID id,
    String serialNumber,
    String name,
    String assetType,
    AssetStatus status,
    UUID assignedTo,
    Instant assignedAt,
    LocalDate purchaseDate,
    LocalDate warrantyExpiryDate,
    String notes,
    Instant createdAt,
    Instant updatedAt,
    List<AssetAssignmentHistoryResponse> history
) {}
