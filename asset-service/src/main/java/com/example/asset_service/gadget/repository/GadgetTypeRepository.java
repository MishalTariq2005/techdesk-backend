package com.example.asset_service.gadget.repository;

import com.example.asset_service.gadget.entity.GadgetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GadgetTypeRepository extends JpaRepository<GadgetType, UUID> {
    boolean existsByNameIgnoreCase(String name);
    Optional<GadgetType> findByNameIgnoreCase(String name);
}
