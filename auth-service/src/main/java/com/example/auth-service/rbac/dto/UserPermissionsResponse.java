package com.example.auth_service.rbac.dto;

import java.util.Set;
import java.util.UUID;

public record UserPermissionsResponse(
    UUID userId,
    String role,
    Set<String> permissions
) {}
