package com.example.asset_service.gadget.service;

import com.example.asset_service.gadget.dto.GadgetTypeRequest;
import com.example.asset_service.gadget.dto.GadgetTypeResponse;
import com.example.asset_service.gadget.entity.GadgetType;
import com.example.asset_service.gadget.exception.GadgetTypeNotFoundException;
import com.example.asset_service.gadget.repository.GadgetTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Same existsByNameIgnoreCase-before-insert pattern as
 * TicketCategoryService.create() — pre-check rather than catching a DB
 * constraint violation, kept consistent across every "must be unique per
 * tenant" entity in this codebase (categories, asset serial numbers, now
 * gadget type names).
 */
@Service
public class GadgetTypeService {

    private final GadgetTypeRepository repository;

    public GadgetTypeService(GadgetTypeRepository repository) {
        this.repository = repository;
    }

    public List<GadgetTypeResponse> listAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public GadgetTypeResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public GadgetTypeResponse create(GadgetTypeRequest request) {
        if (repository.existsByNameIgnoreCase(request.name())) {
            throw new IllegalArgumentException("A gadget type named '" + request.name() + "' already exists");
        }
        GadgetType saved = repository.save(
            new GadgetType(request.name(), request.description(), request.requiresApproval()));
        return toResponse(saved);
    }

    @Transactional
    public GadgetTypeResponse update(UUID id, GadgetTypeRequest request) {
        GadgetType type = findOrThrow(id);
        type.setName(request.name());
        type.setDescription(request.description());
        type.setRequiresApproval(request.requiresApproval());
        return toResponse(type);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new GadgetTypeNotFoundException("Gadget type not found: " + id);
        }
        // Same NOTE as TicketCategoryService.delete(): no guard yet against
        // deleting a type that gadget_requests.gadget_type_id still
        // references — add ON DELETE RESTRICT or an explicit check if this
        // becomes a real problem in practice.
        repository.deleteById(id);
    }

    private GadgetType findOrThrow(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new GadgetTypeNotFoundException("Gadget type not found: " + id));
    }

    private GadgetTypeResponse toResponse(GadgetType t) {
        return new GadgetTypeResponse(t.getId(), t.getName(), t.getDescription(),
            t.isRequiresApproval(), t.getCreatedAt());
    }
}
