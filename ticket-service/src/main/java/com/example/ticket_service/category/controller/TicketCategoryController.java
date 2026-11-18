package com.example.ticket_service.category.controller;

import com.example.ticket_service.category.dto.CategoryRequest;
import com.example.ticket_service.category.dto.CategoryResponse;
import com.example.ticket_service.category.service.TicketCategoryService;
import com.example.ticket_service.rbac.annotation.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ticket-categories")
public class TicketCategoryController {

    private final TicketCategoryService service;

    public TicketCategoryController(TicketCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listAll() {
        // Intentionally NOT @RequiresPermission-gated — every role that
        // creates a ticket (i.e. everyone) needs to read the category list
        // to populate a dropdown. Only mutation is admin-gated below.
        return ResponseEntity.ok(service.listAll());
    }

    @PostMapping
    @RequiresPermission("TICKET_CATEGORY_MANAGE")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PatchMapping("/{id}")
    @RequiresPermission("TICKET_CATEGORY_MANAGE")
    public ResponseEntity<CategoryResponse> update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("TICKET_CATEGORY_MANAGE")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
