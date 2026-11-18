package com.example.file_service.file.service;

import com.example.file_service.client.GadgetRequestLookupClient;
import com.example.file_service.client.TicketLookupClient;
import com.example.file_service.file.entity.FileParentType;
import com.example.file_service.file.exception.FileAccessDeniedException;
import com.example.file_service.rbac.security.CurrentUserContext;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * The 5.3 challenge's actual enforcement point: "a file attached to a
 * ticket is only downloadable by the ticket creator, the assigned IT
 * staff, IT Managers within the same tenant, and COMPANY_ADMIN."
 *
 * Cross-tenant isolation is handled at TWO independent layers, not just
 * here: (1) CurrentUserContextFilter's new tenant-claim cross-check rejects
 * a JWT whose own tenantId doesn't match X-Tenant-ID before any request
 * even reaches this service layer; (2) schema-per-tenant means even a
 * correctly-scoped caller querying their OWN real tenant can never find a
 * FileAttachment row that physically lives in a different tenant's schema
 * — TicketLookupClient/GadgetRequestLookupClient calls go out with THAT
 * caller's real tenant header, so a parent from a different tenant simply
 * won't resolve. This class's job is narrower: given a caller who's
 * already in the right tenant, decide whether they're specifically allowed
 * to touch THIS file.
 *
 * Same rule enforced for BOTH upload and download — if you can't access a
 * parent's files, you can't attach one either.
 */
@Service
public class FileAccessControlService {

    private static final Set<String> MANAGER_ROLES = Set.of("IT_MANAGER", "COMPANY_ADMIN");

    private final TicketLookupClient ticketLookupClient;
    private final GadgetRequestLookupClient gadgetRequestLookupClient;

    public FileAccessControlService(TicketLookupClient ticketLookupClient,
                                     GadgetRequestLookupClient gadgetRequestLookupClient) {
        this.ticketLookupClient = ticketLookupClient;
        this.gadgetRequestLookupClient = gadgetRequestLookupClient;
    }

    /**
     * Throws FileAccessDeniedException if the current caller isn't
     * authorized against the given parent — never returns a boolean, so
     * callers can't accidentally forget to check a return value.
     */
    public void checkAccess(FileParentType parentType, UUID parentId) {
        CurrentUserContext.Identity caller = CurrentUserContext.get();
        if (caller == null) {
            throw new FileAccessDeniedException("No authenticated identity on this request");
        }

        // Role check first, no network call needed — answerable entirely
        // from the caller's own JWT claims.
        if (MANAGER_ROLES.contains(caller.role())) {
            return;
        }

        UUID callerId = UUID.fromString(caller.userId());

        switch (parentType) {
            case TICKET -> checkTicketAccess(callerId, parentId);
            case GADGET_REQUEST -> checkGadgetRequestAccess(callerId, parentId);
        }
    }

    private void checkTicketAccess(UUID callerId, UUID ticketId) {
        TicketLookupClient.TicketSummary ticket = ticketLookupClient.findTicketById(ticketId)
            // Fail closed: covers "ticket-service denied the caller entirely",
            // "ticket doesn't exist", and "ticket-service unreachable" —
            // ALL treated as deny, per TicketLookupClient's own contract.
            .orElseThrow(() -> new FileAccessDeniedException(
                "Cannot verify access to ticket " + ticketId));

        boolean isCreator = callerId.equals(ticket.createdBy());
        boolean isAssignee = ticket.assignedTo() != null && callerId.equals(ticket.assignedTo());

        if (!isCreator && !isAssignee) {
            throw new FileAccessDeniedException(
                "Not authorized to access files on ticket " + ticketId);
        }
    }

    /**
     * ASSUMPTION (documented, not blocking — reversible): the roadmap's
     * explicit access rule only describes ticket-attached files. For
     * gadget-request-attached files, mirroring the same spirit — requester
     * + managers (already handled above) — rather than inventing a
     * different rule the roadmap never specified. No "assigned IT staff"
     * equivalent exists for a gadget request the way it does for a ticket.
     */
    private void checkGadgetRequestAccess(UUID callerId, UUID gadgetRequestId) {
        GadgetRequestLookupClient.GadgetRequestSummary request = gadgetRequestLookupClient
            .findRequestById(gadgetRequestId)
            .orElseThrow(() -> new FileAccessDeniedException(
                "Cannot verify access to gadget request " + gadgetRequestId));

        if (!callerId.equals(request.employeeId())) {
            throw new FileAccessDeniedException(
                "Not authorized to access files on gadget request " + gadgetRequestId);
        }
    }
}
