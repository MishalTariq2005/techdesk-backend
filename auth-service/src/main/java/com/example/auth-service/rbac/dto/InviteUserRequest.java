package com.example.auth_service.rbac.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteUserRequest(
    @NotBlank @Email String email,
    @NotBlank String role,
    String department
) {}
