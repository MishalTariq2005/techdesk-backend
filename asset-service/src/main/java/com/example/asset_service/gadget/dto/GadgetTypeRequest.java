package com.example.asset_service.gadget.dto;

import jakarta.validation.constraints.NotBlank;

public record GadgetTypeRequest(
    @NotBlank String name,
    String description,
    boolean requiresApproval
) {}
