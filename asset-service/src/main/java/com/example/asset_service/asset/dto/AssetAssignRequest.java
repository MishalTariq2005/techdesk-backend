package com.example.asset_service.asset.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssetAssignRequest(
    @NotNull UUID employeeId
) {}
