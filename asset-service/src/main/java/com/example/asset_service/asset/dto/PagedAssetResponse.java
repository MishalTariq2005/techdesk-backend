package com.example.asset_service.asset.dto;

import java.util.List;

public record PagedAssetResponse(
    List<AssetResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
