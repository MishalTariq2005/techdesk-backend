package com.example.asset_service.gadget.repository;

import com.example.asset_service.gadget.entity.GadgetRequestDecisionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GadgetRequestDecisionLogRepository extends JpaRepository<GadgetRequestDecisionLog, UUID> {
    // Ascending, not descending like AssetAssignmentHistory's default display
    // order — a replay/reconstruction view specifically wants chronological
    // order (first event first), unlike a "recent activity" feed.
    List<GadgetRequestDecisionLog> findByGadgetRequestIdOrderByEventTimestampAsc(UUID gadgetRequestId);
}
