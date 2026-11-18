package com.example.asset_service.gadget.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateGadgetRequestRequest(
    @NotNull UUID gadgetTypeId,
    String reason
) {}
