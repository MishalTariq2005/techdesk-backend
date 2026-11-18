package com.example.asset_service.asset.repository;

import com.example.asset_service.asset.entity.Asset;
import com.example.asset_service.asset.entity.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID>, JpaSpecificationExecutor<Asset> {

    boolean existsBySerialNumberIgnoreCase(String serialNumber);

    List<Asset> findByWarrantyExpiryDateBetweenAndDeletedAtIsNull(LocalDate from, LocalDate to);

    /**
     * NEW — supports 5.2's gadget-request availability check. Matches
     * purely by the assetType string against GadgetType.name (see
     * GadgetType's doc comment on why there's no formal FK between the two
     * concepts). findFirst, not findAll: GadgetRequestService only ever
     * needs one candidate to reserve, and picking the first by natural
     * ordering is enough — no scoring/ranking need the way 4.4's staff
     * auto-assignment had one.
     */
    Optional<Asset> findFirstByAssetTypeIgnoreCaseAndStatusAndDeletedAtIsNull(String assetType, AssetStatus status);
}

