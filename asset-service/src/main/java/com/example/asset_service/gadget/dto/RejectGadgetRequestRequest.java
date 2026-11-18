package com.example.asset_service.gadget.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectGadgetRequestRequest(
    @NotBlank String reason
) {}
