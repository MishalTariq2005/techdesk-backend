package com.example.auth_service.rbac.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String role,
    String department,
    String status,
    Instant createdAt
) {}
