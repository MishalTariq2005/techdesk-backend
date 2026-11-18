package com.example.asset_service.gadget.service;

import com.example.asset_service.asset.dto.AssetAssignRequest;
import com.example.asset_service.asset.entity.Asset;
import com.example.asset_service.client.UserLookupClient;
import com.example.asset_service.asset.entity.AssetStatus;
import com.example.asset_service.asset.exception.AssetAlreadyAssignedException;
import com.example.asset_service.asset.repository.AssetRepository;
import com.example.asset_service.asset.service.AssetService;
import com.example.asset_service.client.NotificationClient;
import com.example.asset_service.gadget.dto.*;
import com.example.asset_service.gadget.entity.GadgetRequest;
import com.example.asset_service.gadget.entity.GadgetRequestDecisionLog;
import com.example.asset_service.gadget.entity.GadgetType;
import com.example.asset_service.gadget.exception.GadgetRequestNotFoundException;
import com.example.asset_service.gadget.exception.GadgetTypeNotFoundException;
import com.example.asset_service.gadget.exception.InvalidGadgetRequestTransitionException;
import com.example.asset_service.gadget.repository.GadgetRequestDecisionLogRepository;
import com.example.asset_service.gadget.repository.GadgetRequestRepository;
import com.example.asset_service.gadget.repository.GadgetTypeRepository;
import com.example.asset_service.rbac.exception.InsufficientPermissionException;
import com.example.asset_service.rbac.security.CurrentUserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The 5.2 challenge lives here: "given the audit log for a gadget request,
 * an external reviewer must be able to reconstruct the exact decision
 * timeline... with no information gaps." Every state transition below —
 * human-triggered or system-triggered — is checked against
 * GadgetRequestStatusTransitionValidator's map AND writes exactly one
 * GadgetRequestDecisionLog row, in the same transaction as the actual
 * status change. There is no code path that mutates GadgetRequest.status
 * without both a validated transition and a log row for it.
 *
 * IMPORTANT design point the map enforces: APPROVED is ONLY ever entered
 * on the path where an asset is actually about to be reserved (it has no
 * outbound edge to IN_PROCUREMENT). The "no stock" outcome transitions
 * directly from REQUESTED/PENDING_MANAGER_APPROVAL/IN_PROCUREMENT straight
 * to IN_PROCUREMENT — APPROVED is never a transient resting point on that
 * branch, even in the log.
 *
 * Calls AssetService.assign() directly — a plain in-process method call,
 * same transaction, same schema. This is the entire reason 5.2 lives inside
 * asset-service rather than as its own service: a separate gadget-service
 * calling asset-service over HTTP to check-and-reserve an asset would split
 * "did we actually reserve it" and "did we record that we reserved it"
 * across two independent transactions — exactly the kind of gap the
 * audit-replay challenge is testing for.
 */
@Service
public class GadgetRequestService {

    private final GadgetRequestRepository requestRepository;
    private final GadgetTypeRepository typeRepository;
    private final GadgetRequestDecisionLogRepository logRepository;
    private final AssetRepository assetRepository;
    private final AssetService assetService;
    private final GadgetRequestStatusTransitionValidator validator;
    private final NotificationClient notificationClient;
    private final UserLookupClient userLookupClient;

public GadgetRequestService(GadgetRequestRepository requestRepository,
                                 GadgetTypeRepository typeRepository,
                                 GadgetRequestDecisionLogRepository logRepository,
                                 AssetRepository assetRepository,
                                 AssetService assetService,
                                 GadgetRequestStatusTransitionValidator validator,
                                 NotificationClient notificationClient,
                                 UserLookupClient userLookupClient) {
        this.requestRepository = requestRepository;
        this.typeRepository = typeRepository;
        this.logRepository = logRepository;
        this.assetRepository = assetRepository;
        this.assetService = assetService;
        this.validator = validator;
        this.notificationClient = notificationClient;
        this.userLookupClient = userLookupClient;
    }

    public PagedGadgetRequestResponse list(String status, UUID employeeId, int page, int size) {
        Specification<GadgetRequest> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        if (employeeId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("employeeId"), employeeId));
        }
        Page<GadgetRequest> result = requestRepository.findAll(spec, PageRequest.of(page, size));
        List<GadgetRequestResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return new PagedGadgetRequestResponse(content, result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages());
    }

    public GadgetRequestResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    /**
     * The full reconstructable timeline for the audit-replay challenge — the
     * current request state plus every logged transition in chronological
     * order, in one response.
     */
    public GadgetRequestDetailResponse getAuditReplay(UUID id) {
        GadgetRequest request = findOrThrow(id);
        List<GadgetRequestDecisionResponse> timeline = logRepository
            .findByGadgetRequestIdOrderByEventTimestampAsc(id).stream()
            .map(this::toDecisionResponse)
            .toList();
        return new GadgetRequestDetailResponse(toResponse(request), timeline);
    }


    @Transactional
    public GadgetRequestResponse create(CreateGadgetRequestRequest dto) {
        UUID employeeId = currentUserId();
        GadgetType type = typeRepository.findById(dto.gadgetTypeId())
            .orElseThrow(() -> new GadgetTypeNotFoundException("Gadget type not found: " + dto.gadgetTypeId()));
        GadgetRequest request = requestRepository.save(new GadgetRequest(type.getId(), employeeId, dto.reason()));
        log(request.getId(), null, "REQUESTED", employeeId, null, null);
        if (type.isRequiresApproval()) {
            validator.validate("REQUESTED", "PENDING_MANAGER_APPROVAL");
            log(request.getId(), "REQUESTED", "PENDING_MANAGER_APPROVAL", null,
                "Routed to manager approval: " + type.getName() + " requires approval", null);
            request.setStatus("PENDING_MANAGER_APPROVAL");

            // NEW: notify every manager/admin that a request is waiting on
            // them — previously nobody in a decision-making role was ever
            // told anything was pending, only the outcome (approve/reject)
            // reached the employee. Best-effort broadcast: findUsersByRole()
            // never throws, so an auth-service hiccup here can't block the
            // request itself from being created.
            notifyManagersOfPendingRequest(request, type);
        } else {
            // Auto-approval path (assumption, documented up front when this
            // feature was scoped): skips the human gate entirely and enters
            // the same availability-check outcome a manager approval would
            // trigger, logged under a null actor with an explicit reason so
            // the replay timeline shows *why* it moved forward without a
            // human decision.
            resolveAvailability(request, type, "REQUESTED", null);
        }
        return toResponse(request);
    }

    /**
     * Broadcasts to every COMPANY_ADMIN and IT_MANAGER in the tenant —
     * the first broadcast-style notification in this codebase (everything
     * else notifies exactly one recipient). Deliberately notifies BOTH
     * roles rather than just IT_MANAGER, since COMPANY_ADMIN also holds
     * GADGET_APPROVE and can act on this request too.
     */
    private void notifyManagersOfPendingRequest(GadgetRequest request, GadgetType type) {
        var admins = userLookupClient.findUsersByRole("COMPANY_ADMIN");
        var managers = userLookupClient.findUsersByRole("IT_MANAGER");

        String title = "Gadget request awaiting approval";
        String message = "A request for \"" + type.getName() + "\" is waiting for your approval.";

        admins.forEach(u -> notificationClient.notify(u.id(), "GADGET_REQUEST_PENDING_APPROVAL",
            title, message, "GADGET_REQUEST", request.getId()));
        managers.forEach(u -> notificationClient.notify(u.id(), "GADGET_REQUEST_PENDING_APPROVAL",
            title, message, "GADGET_REQUEST", request.getId()));
    }

    @Transactional
    public GadgetRequestResponse approve(UUID id) {
        UUID actorId = currentUserId();
        GadgetRequest request = findOrThrow(id);
        GadgetType type = typeRepository.findById(request.getGadgetTypeId())
            .orElseThrow(() -> new GadgetTypeNotFoundException("Gadget type not found: " + request.getGadgetTypeId()));

        // Action-specific precondition, deliberately stricter than what the
        // validator's map alone would allow: PENDING_MANAGER_APPROVAL also
        // has outbound edges the auto-approval and retry code paths use,
        // but THIS action (a human clicking approve) only ever applies from
        // one specific state.
        if (!"PENDING_MANAGER_APPROVAL".equals(request.getStatus())) {
            throw new InvalidGadgetRequestTransitionException(
                "Cannot approve a request in status " + request.getStatus());
        }

        resolveAvailability(request, type, "PENDING_MANAGER_APPROVAL", actorId);

        // Notification type is the roadmap's literal "gadget request
        // approved" event — but the MESSAGE reflects the actual outcome
        // (ready now vs. sent to procurement), since resolveAvailability
        // already decided that atomically above. A requester told simply
        // "approved" with no indication of which outcome they got would be
        // less useful than what's actually knowable by this point.
        String outcomeMessage = "ASSIGNED".equals(request.getStatus())
            ? "Your request for " + type.getName() + " was approved and has been assigned to you."
            : "Your request for " + type.getName() + " was approved. No unit was available, so it's been sent to procurement.";
        notificationClient.notify(request.getEmployeeId(), "GADGET_REQUEST_APPROVED",
            "Gadget request approved", outcomeMessage, "GADGET_REQUEST", request.getId());

        return toResponse(request);
    }

    /**
     * Manual trigger for "the purchase arrived, check again" — no
     * procurement system exists to call back automatically (same reasoning
     * 4.4 used for staff_profiles: no message bus, so a human triggers the
     * explicit endpoint instead).
     */
    @Transactional
    public GadgetRequestResponse retryProcurement(UUID id) {
        UUID actorId = currentUserId();
        GadgetRequest request = findOrThrow(id);
        GadgetType type = typeRepository.findById(request.getGadgetTypeId())
            .orElseThrow(() -> new GadgetTypeNotFoundException("Gadget type not found: " + request.getGadgetTypeId()));

        if (!"IN_PROCUREMENT".equals(request.getStatus())) {
            throw new InvalidGadgetRequestTransitionException(
                "Cannot retry procurement for a request in status " + request.getStatus());
        }

        Optional<Asset> reserved = tryReserveAsset(request, type);
        if (reserved.isPresent()) {
            completeAssignment(request, reserved.get(), "IN_PROCUREMENT", actorId);
        } else {
            // No self-edge for IN_PROCUREMENT in the validator's map by
            // design (see GadgetRequestStatusTransitionValidator's doc
            // comment) — log directly rather than calling validator.validate(),
            // which would reject IN_PROCUREMENT -> IN_PROCUREMENT as
            // "invalid" even though it's a legitimate no-op retry outcome,
            // not a data gap.
            log(request.getId(), "IN_PROCUREMENT", "IN_PROCUREMENT", actorId,
                "Procurement check retried — still no matching asset available", null);
        }

        return toResponse(request);
    }

    @Transactional
    public GadgetRequestResponse reject(UUID id, RejectGadgetRequestRequest dto) {
        UUID actorId = currentUserId();
        GadgetRequest request = findOrThrow(id);
        String from = request.getStatus();

        // REJECTED is reachable only from PENDING_MANAGER_APPROVAL in the
        // map — validator.validate() alone is a complete precondition check
        // here, unlike approve()/retryProcurement() which need an extra
        // action-specific guard on top.
        validator.validate(from, "REJECTED");

        log(request.getId(), from, "REJECTED", actorId, dto.reason(), null);
        request.setStatus("REJECTED");
        request.setRejectionReason(dto.reason());

        notificationClient.notify(request.getEmployeeId(), "GADGET_REQUEST_REJECTED",
            "Gadget request rejected", "Your gadget request was rejected: " + dto.reason(),
            "GADGET_REQUEST", request.getId());

        return toResponse(request);
    }

    @Transactional
    public GadgetRequestResponse cancel(UUID id) {
        UUID callerId = currentUserId();
        GadgetRequest request = findOrThrow(id);

        // Not requested explicitly in the roadmap text; included because
        // GadgetRequestStatusTransitionValidator's map already models
        // CANCELLED and leaving it unreachable would be dead code.
        if (!request.getEmployeeId().equals(callerId)) {
            throw new InsufficientPermissionException("Only the original requester can cancel this request");
        }

        String from = request.getStatus();
        // CANCELLED is reachable only from REQUESTED and
        // PENDING_MANAGER_APPROVAL — validator.validate() alone is a
        // complete precondition check here too.
        validator.validate(from, "CANCELLED");

        log(request.getId(), from, "CANCELLED", callerId, null, null);
        request.setStatus("CANCELLED");

        return toResponse(request);
    }

    /**
     * Checks availability and routes to whichever outcome the map actually
     * allows from fromStatus: either the full APPROVED -> READY_FOR_PICKUP
     * -> ASSIGNED chain, or a direct jump to IN_PROCUREMENT. Shared by
     * create()'s auto-approval branch and approve() — the two places a
     * request first becomes eligible to check for an asset.
     */
    private void resolveAvailability(GadgetRequest request, GadgetType type, String fromStatus, UUID actorId) {
        Optional<Asset> reserved = tryReserveAsset(request, type);
        if (reserved.isPresent()) {
            completeAssignment(request, reserved.get(), fromStatus, actorId);
        } else {
            validator.validate(fromStatus, "IN_PROCUREMENT");
            log(request.getId(), fromStatus, "IN_PROCUREMENT", actorId,
                "No matching asset available for type '" + type.getName() + "' — sent to procurement", null);
            request.setStatus("IN_PROCUREMENT");
        }
    }

    /**
     * Pure "try to get one" — no status mutation, no logging. Returns the
     * asset actually reserved (already assigned via AssetService, in this
     * same transaction) or empty if nothing matched or a concurrent request
     * won the race.
     */
    private Optional<Asset> tryReserveAsset(GadgetRequest request, GadgetType type) {
        Optional<Asset> available = assetRepository
            .findFirstByAssetTypeIgnoreCaseAndStatusAndDeletedAtIsNull(type.getName(), AssetStatus.AVAILABLE);
        if (available.isEmpty()) {
            return Optional.empty();
        }
        Asset asset = available.get();
        try {
            assetService.assign(asset.getId(), new AssetAssignRequest(request.getEmployeeId()));
            return Optional.of(asset);
        } catch (AssetAlreadyAssignedException raceLost) {
            // Genuine race: another gadget-request approval (or a direct
            // /api/assets/{id}/assign call) claimed this exact asset between
            // our availability query and this call — same class of race
            // Asset.version exists to catch. Treated identically to "nothing
            // was available," which is accurate: from this request's
            // perspective, nothing usable was actually available.
            return Optional.empty();
        }
    }

    /**
     * The APPROVED -> READY_FOR_PICKUP -> ASSIGNED chain, walked atomically
     * in one call (see GadgetRequestStatusTransitionValidator's doc comment
     * — the roadmap describes this as one arrow, not three separately
     * triggered actions). Only ever called once an asset has actually been
     * reserved — APPROVED never gets logged or persisted on any path that
     * doesn't end here.
     */
    private void completeAssignment(GadgetRequest request, Asset asset, String fromStatus, UUID actorId) {
        validator.validate(fromStatus, "APPROVED");
        log(request.getId(), fromStatus, "APPROVED", actorId, null, null);
        request.setStatus("APPROVED");
        request.setAssignedAssetId(asset.getId());

        validator.validate("APPROVED", "READY_FOR_PICKUP");
        log(request.getId(), "APPROVED", "READY_FOR_PICKUP", null,
            "Asset reserved: " + asset.getSerialNumber(), asset.getId());
        request.setStatus("READY_FOR_PICKUP");

        validator.validate("READY_FOR_PICKUP", "ASSIGNED");
        log(request.getId(), "READY_FOR_PICKUP", "ASSIGNED", null,
            "Handed over to employee", asset.getId());
        request.setStatus("ASSIGNED");
    }

    private void log(UUID requestId, String from, String to, UUID actorId, String reason, UUID assetId) {
        logRepository.save(new GadgetRequestDecisionLog(requestId, from, to, actorId, reason, assetId));
    }

    private GadgetRequest findOrThrow(UUID id) {
        return requestRepository.findById(id)
            .orElseThrow(() -> new GadgetRequestNotFoundException("Gadget request not found: " + id));
    }

    private UUID currentUserId() {
        return UUID.fromString(CurrentUserContext.get().userId());
    }

    private GadgetRequestResponse toResponse(GadgetRequest r) {
        String typeName = typeRepository.findById(r.getGadgetTypeId())
            .map(GadgetType::getName)
            .orElse(null);
        return new GadgetRequestResponse(r.getId(), r.getGadgetTypeId(), typeName, r.getEmployeeId(),
            r.getReason(), r.getStatus(), r.getAssignedAssetId(), r.getRejectionReason(),
            r.getCreatedAt(), r.getUpdatedAt());
    }

    private GadgetRequestDecisionResponse toDecisionResponse(GadgetRequestDecisionLog log) {
        return new GadgetRequestDecisionResponse(log.getFromStatus(), log.getToStatus(),
            log.getActorUserId(), log.getReason(), log.getAssetId(), log.getEventTimestamp());
    }
}