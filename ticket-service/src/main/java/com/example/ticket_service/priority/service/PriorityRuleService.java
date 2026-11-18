package com.example.ticket_service.priority.service;

import com.example.ticket_service.priority.dto.PriorityRuleRequest;
import com.example.ticket_service.priority.dto.PriorityRuleResponse;
import com.example.ticket_service.priority.entity.PriorityRule;
import com.example.ticket_service.priority.repository.PriorityRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PriorityRuleService {

    private static final Set<String> VALID_PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    private final PriorityRuleRepository repository;

    public PriorityRuleService(PriorityRuleRepository repository) {
        this.repository = repository;
    }

    public List<PriorityRuleResponse> listAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public PriorityRuleResponse create(PriorityRuleRequest request) {
        validatePriority(request.priority());
        PriorityRule saved = repository.save(new PriorityRule(
            request.categoryId(), request.keyword(), request.reporterDepartment(),
            request.priority().toUpperCase(), request.weight()
        ));
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Priority rule not found: " + id);
        }
        repository.deleteById(id);
    }

    private void validatePriority(String priority) {
        if (priority == null || !VALID_PRIORITIES.contains(priority.toUpperCase())) {
            throw new IllegalArgumentException("priority must be one of " + VALID_PRIORITIES);
        }
    }

    private PriorityRuleResponse toResponse(PriorityRule r) {
        return new PriorityRuleResponse(r.getId(), r.getCategoryId(), r.getKeyword(),
            r.getReporterDepartment(), r.getPriority(), r.getWeight(), r.getCreatedAt());
    }
}
