package com.example.auth_service.rbac.dto;

import java.util.List;

public record PagedUserResponse(
    List<UserResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
