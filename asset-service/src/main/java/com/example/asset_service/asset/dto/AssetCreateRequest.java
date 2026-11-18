package com.example.asset_service.asset.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record AssetCreateRequest(
    @NotBlank String serialNumber,
    @NotBlank String name,
    String assetType,
    LocalDate purchaseDate,
    LocalDate warrantyExpiryDate,
    String notes
) {}
