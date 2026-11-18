package com.example.asset_service.asset.service;

import com.example.asset_service.asset.dto.*;
import com.example.asset_service.asset.entity.Asset;
import com.example.asset_service.asset.entity.AssetAssignmentEventType;
import com.example.asset_service.asset.entity.AssetAssignmentHistory;
import com.example.asset_service.asset.entity.AssetStatus;
import com.example.asset_service.asset.exception.AssetAlreadyAssignedException;
import com.example.asset_service.asset.exception.AssetNotFoundException;
import com.example.asset_service.asset.exception.DuplicateSerialNumberException;
import com.example.asset_service.asset.repository.AssetAssignmentHistoryRepository;
import com.example.asset_service.asset.repository.AssetRepository;
import com.example.asset_service.client.UserLookupClient;
import com.example.asset_service.rbac.security.CurrentUserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetAssignmentHistoryRepository historyRepository;
    private final UserLookupClient userLookupClient;

    public AssetService(AssetRepository assetRepository,
                         AssetAssignmentHistoryRepository historyRepository,
                         UserLookupClient userLookupClient) {
        this.assetRepository = assetRepository;
        this.historyRepository = historyRepository;
        this.userLookupClient = userLookupClient;
    }

    public PagedAssetResponse list(AssetStatus status, String assetType, UUID assignedTo,
                                    int page, int size) {
        Specification<Asset> spec = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        if (assetType != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("assetType"), assetType));
        }
        if (assignedTo != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("assignedTo"), assignedTo));
        }

        Page<Asset> result = assetRepository.findAll(spec, PageRequest.of(page, size));
        List<AssetResponse> content = result.getContent().stream()
            .map(a -> toResponse(a, List.of())) // history omitted on list view — fetch via detail
            .toList();

        return new PagedAssetResponse(content, result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages());
    }

    public AssetResponse getById(UUID id) {
        Asset asset = findActiveOrThrow(id);
        List<AssetAssignmentHistoryResponse> history = historyRepository
            .findByAssetIdOrderByEventTimestampDesc(id).stream()
            .map(this::toHistoryResponse)
            .toList();
        return toResponse(asset, history);
    }

    @Transactional
    public AssetResponse create(AssetCreateRequest request) {
        if (assetRepository.existsBySerialNumberIgnoreCase(request.serialNumber())) {
            throw new DuplicateSerialNumberException(
                "An asset with serial number '" + request.serialNumber() + "' already exists");
        }
        Asset saved = assetRepository.save(new Asset(
            request.serialNumber(), request.name(), request.assetType(),
            request.purchaseDate(), request.warrantyExpiryDate(), request.notes()));
        return toResponse(saved, List.of());
    }

    @Transactional
    public AssetResponse update(UUID id, AssetUpdateRequest request) {
        Asset asset = findActiveOrThrow(id);
        asset.setName(request.name());
        asset.setAssetType(request.assetType());
        asset.setPurchaseDate(request.purchaseDate());
        asset.setWarrantyExpiryDate(request.warrantyExpiryDate());
        asset.setNotes(request.notes());
        asset.touch();
        return toResponse(asset, List.of());
    }

    @Transactional
    public void softDelete(UUID id) {
        Asset asset = findActiveOrThrow(id);
        asset.setDeletedAt(Instant.now());
    }

    /**
     * The 5.1 challenge's core conflict guard: if the asset is already
     * ASSIGNED, look up the current assignee's name (best-effort — see
     * UserLookupClient) and fail with a 409 carrying that detail, rather
     * than silently overwriting the existing assignment.
     */
    @Transactional
    public AssetResponse assign(UUID id, AssetAssignRequest request) {
        Asset asset = findActiveOrThrow(id);
        CurrentUserContext.Identity actor = CurrentUserContext.get();

        if (asset.getStatus() == AssetStatus.ASSIGNED) {
            String currentAssigneeName = userLookupClient.findUserById(asset.getAssignedTo())
                .map(UserLookupClient.UserLookupResponse::fullName)
                .orElse(null);
            throw new AssetAlreadyAssignedException(
                "Asset " + asset.getSerialNumber() + " is already assigned",
                asset.getAssignedTo(), currentAssigneeName, asset.getAssignedAt());
        }

        asset.setAssignedTo(request.employeeId());
        asset.setAssignedAt(Instant.now());
        asset.setStatus(AssetStatus.ASSIGNED);
        asset.touch();

        historyRepository.save(new AssetAssignmentHistory(
            asset.getId(), request.employeeId(), AssetAssignmentEventType.ASSIGNED,
            UUID.fromString(actor.userId()), null));

        return toResponse(asset, List.of());
    }

    @Transactional
    public AssetResponse unassign(UUID id) {
        Asset asset = findActiveOrThrow(id);
        CurrentUserContext.Identity actor = CurrentUserContext.get();

        if (asset.getStatus() != AssetStatus.ASSIGNED || asset.getAssignedTo() == null) {
            throw new IllegalArgumentException("Asset " + asset.getSerialNumber() + " is not currently assigned");
        }

        UUID previousAssignee = asset.getAssignedTo();
        asset.setAssignedTo(null);
        asset.setAssignedAt(null);
        asset.setStatus(AssetStatus.AVAILABLE);
        asset.touch();

        historyRepository.save(new AssetAssignmentHistory(
            asset.getId(), previousAssignee, AssetAssignmentEventType.RETURNED,
            UUID.fromString(actor.userId()), null));

        return toResponse(asset, List.of());
    }

    private Asset findActiveOrThrow(UUID id) {
        return assetRepository.findById(id)
            .filter(a -> a.getDeletedAt() == null)
            .orElseThrow(() -> new AssetNotFoundException("Asset not found: " + id));
    }

    private AssetResponse toResponse(Asset a, List<AssetAssignmentHistoryResponse> history) {
        return new AssetResponse(a.getId(), a.getSerialNumber(), a.getName(), a.getAssetType(),
            a.getStatus(), a.getAssignedTo(), a.getAssignedAt(), a.getPurchaseDate(),
            a.getWarrantyExpiryDate(), a.getNotes(), a.getCreatedAt(), a.getUpdatedAt(), history);
    }

    private AssetAssignmentHistoryResponse toHistoryResponse(AssetAssignmentHistory h) {
        return new AssetAssignmentHistoryResponse(h.getId(), h.getEmployeeId(), h.getEventType(),
            h.getEventTimestamp(), h.getActorUserId(), h.getNotes());
    }
}
