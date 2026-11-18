package com.example.ticket_service.priority.controller;

import com.example.ticket_service.priority.dto.*;
import com.example.ticket_service.priority.service.PriorityEngineService;
import com.example.ticket_service.priority.service.PriorityRuleService;
import com.example.ticket_service.rbac.annotation.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PriorityRuleController {

    private final PriorityRuleService ruleService;
    private final PriorityEngineService engineService;

    public PriorityRuleController(PriorityRuleService ruleService, PriorityEngineService engineService) {
        this.ruleService = ruleService;
        this.engineService = engineService;
    }

    @GetMapping("/priority-rules")
    @RequiresPermission("TICKET_CATEGORY_MANAGE")
    public ResponseEntity<List<PriorityRuleResponse>> listAll() {
        return ResponseEntity.ok(ruleService.listAll());
    }

    @PostMapping("/priority-rules")
    @RequiresPermission("TICKET_CATEGORY_MANAGE")
    public ResponseEntity<PriorityRuleResponse> create(@Valid @RequestBody PriorityRuleRequest request) {
        return ResponseEntity.ok(ruleService.create(request));
    }

    @DeleteMapping("/priority-rules/{id}")
    @RequiresPermission("TICKET_CATEGORY_MANAGE")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ruleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Dry-run: no ticket is created, no @RequiresPermission — any
     * authenticated tenant user should be able to preview what priority
     * their in-progress ticket description would get before submitting.
     */
    @PostMapping("/tickets/priority-preview")
    public ResponseEntity<PriorityPreviewResponse> preview(@Valid @RequestBody PriorityPreviewRequest request) {
        return ResponseEntity.ok(engineService.evaluate(request));
    }
}
