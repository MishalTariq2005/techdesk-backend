package com.example.asset_service.asset.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * Deliberately excludes serialNumber (identity — shouldn't silently change
 * post-creation) and status (only changes via /assign, /return, or a future
 * dedicated repair/retire endpoint — not a generic field edit). Scope
 * decision, not an oversight.
 */
public record AssetUpdateRequest(
    @NotBlank String name,
    String assetType,
    LocalDate purchaseDate,
    LocalDate warrantyExpiryDate,
    String notes
) {}
