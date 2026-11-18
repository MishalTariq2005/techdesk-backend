package com.example.asset_service.asset.repository;

import com.example.asset_service.asset.entity.AssetAssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssetAssignmentHistoryRepository extends JpaRepository<AssetAssignmentHistory, UUID> {
    List<AssetAssignmentHistory> findByAssetIdOrderByEventTimestampDesc(UUID assetId);
}
