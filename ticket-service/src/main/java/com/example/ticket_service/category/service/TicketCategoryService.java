package com.example.ticket_service.category.service;

import com.example.ticket_service.category.dto.CategoryRequest;
import com.example.ticket_service.category.dto.CategoryResponse;
import com.example.ticket_service.category.entity.TicketCategory;
import com.example.ticket_service.category.repository.TicketCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TicketCategoryService {

    private final TicketCategoryRepository repository;

    public TicketCategoryService(TicketCategoryRepository repository) {
        this.repository = repository;
    }

    public List<CategoryResponse> listAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (repository.existsByNameIgnoreCase(request.name())) {
            throw new IllegalArgumentException("A category named '" + request.name() + "' already exists");
        }
        TicketCategory saved = repository.save(new TicketCategory(request.name(), request.description()));
        return toResponse(saved);
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        TicketCategory category = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
        category.setName(request.name());
        category.setDescription(request.description());
        return toResponse(category);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Category not found: " + id);
        }
        // NOTE: no FK guard yet against priority_rules.category_id or (future)
        // tickets.category_id — add ON DELETE RESTRICT or an explicit check
        // here once tickets exist (4.3), so deleting a category in use fails
        // loudly instead of leaving orphaned references.
        repository.deleteById(id);
    }

    private CategoryResponse toResponse(TicketCategory c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getCreatedAt());
    }
}
