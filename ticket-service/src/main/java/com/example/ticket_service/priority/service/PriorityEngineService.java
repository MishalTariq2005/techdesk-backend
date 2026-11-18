package com.example.ticket_service.priority.service;

import com.example.ticket_service.priority.dto.PriorityPreviewRequest;
import com.example.ticket_service.priority.dto.PriorityPreviewResponse;
import com.example.ticket_service.priority.entity.PriorityRule;
import com.example.ticket_service.priority.repository.PriorityRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Evaluation algorithm (roadmap 4.2 challenge):
 * 1. Find every rule that matches (category/keyword/department all satisfied
 *    or wildcarded).
 * 2. Among matches, take the ones at the HIGHEST weight — not first-match.
 * 3. If exactly one rule is at that top weight, it wins outright.
 * 4. If multiple rules tie at the top weight:
 *    a. If they all agree on the same priority, there's no real conflict —
 *       just multiple rules covering the same case. Pick any of them.
 *    b. If they disagree on priority, that's a genuine authoring conflict.
 *       Break the tie by specificity (more non-wildcard conditions wins) —
 *       a rule that names category+keyword+department is a more deliberate
 *       match than one that only names a keyword. If specificity ALSO ties,
 *       fall back to the highest-severity priority among them (CRITICAL >
 *       HIGH > MEDIUM > LOW) as a fail-safe-loud default, and log the
 *       ambiguity — this is the "rule conflict resolver" the roadmap asks
 *       for. TODO: once audit_logs exists (6.3), replace this SLF4J log
 *       with a real AuditEvent so conflicts are queryable, not just visible
 *       in service logs.
 * 5. No rule matches at all -> MEDIUM, the documented default.
 */
@Service
public class PriorityEngineService {

    private static final Logger log = LoggerFactory.getLogger(PriorityEngineService.class);
    private static final String DEFAULT_PRIORITY = "MEDIUM";
    private static final List<String> SEVERITY_ORDER = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final PriorityRuleRepository ruleRepository;

    public PriorityEngineService(PriorityRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public PriorityPreviewResponse evaluate(PriorityPreviewRequest request) {
        List<PriorityRule> allRules = ruleRepository.findAll();

        List<PriorityRule> matching = allRules.stream()
            .filter(r -> r.matches(request.categoryId(), request.description(), request.reporterDepartment()))
            .toList();

        if (matching.isEmpty()) {
            return new PriorityPreviewResponse(DEFAULT_PRIORITY, null, false, List.of(),
                "No rules matched; falling back to default priority " + DEFAULT_PRIORITY);
        }

        int topWeight = matching.stream().mapToInt(PriorityRule::getWeight).max().orElse(0);
        List<PriorityRule> topMatches = matching.stream()
            .filter(r -> r.getWeight() == topWeight)
            .toList();

        if (topMatches.size() == 1) {
            PriorityRule winner = topMatches.get(0);
            return new PriorityPreviewResponse(winner.getPriority(), winner.getId(), false, List.of(),
                "Single highest-weight rule matched (weight=" + topWeight + ")");
        }

        boolean allSamePriority = topMatches.stream().map(PriorityRule::getPriority).distinct().count() == 1;
        if (allSamePriority) {
            PriorityRule winner = topMatches.get(0);
            return new PriorityPreviewResponse(winner.getPriority(), winner.getId(), false,
                topMatches.stream().map(PriorityRule::getId).toList(),
                topMatches.size() + " rules tied at weight=" + topWeight + " but agree on priority " + winner.getPriority());
        }

        // Genuine conflict: same weight, different priorities. Resolve by specificity,
        // then by highest severity, and log it either way.
        PriorityRule resolved = topMatches.stream()
            .max(Comparator.comparingInt(PriorityRule::specificity)
                .thenComparingInt(r -> SEVERITY_ORDER.indexOf(r.getPriority())))
            .orElseThrow();

        List<UUID> conflictIds = topMatches.stream().map(PriorityRule::getId).toList();
        log.warn("Ambiguous priority rule match: {} rules tied at weight={} with different priorities {} — "
                + "resolved to rule {} (priority={}) by specificity/severity tie-break. Conflicting rule IDs: {}",
            topMatches.size(), topWeight,
            topMatches.stream().map(PriorityRule::getPriority).distinct().toList(),
            resolved.getId(), resolved.getPriority(), conflictIds);

        return new PriorityPreviewResponse(resolved.getPriority(), resolved.getId(), true, conflictIds,
            "AMBIGUOUS: " + topMatches.size() + " rules tied at weight=" + topWeight
                + " with conflicting priorities — resolved via specificity/severity tie-break, logged for review");
    }
}
