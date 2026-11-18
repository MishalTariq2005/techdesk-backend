package com.example.asset_service.gadget.dto;

import java.util.List;

/**
 * GET /api/gadget-requests/{id}/audit-replay returns this — the request's
 * current state plus its FULL decision timeline in order, so a reviewer can
 * reconstruct everything from one response with no follow-up queries. This
 * is the concrete artifact 5.2's audit-replay challenge is testing.
 */
public record GadgetRequestDetailResponse(
    GadgetRequestResponse request,
    List<GadgetRequestDecisionResponse> timeline
) {}
