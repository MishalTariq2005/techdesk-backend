package com.example.asset_service.gadget.dto;

import java.util.List;

public record PagedGadgetRequestResponse(
    List<GadgetRequestResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
