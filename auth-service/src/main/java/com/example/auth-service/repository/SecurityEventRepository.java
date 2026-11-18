package com.example.auth_service.repository;

import com.example.auth_service.entity.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {
}