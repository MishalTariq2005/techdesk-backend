package com.example.auth_service.rbac.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    private String description;

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
}
