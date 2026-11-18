package com.example.asset_service.gadget.repository;

import com.example.asset_service.gadget.entity.GadgetRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface GadgetRequestRepository extends JpaRepository<GadgetRequest, UUID>, JpaSpecificationExecutor<GadgetRequest> {
}
