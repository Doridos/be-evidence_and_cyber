package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dto.*;
import cz.fel.cvut.beevidence_and_cyber.service.CurrentUserService;
import cz.fel.cvut.beevidence_and_cyber.service.DetectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DetectionController {

    private final DetectionService detectionService;
    private final CurrentUserService currentUserService;

    @GetMapping("/detection-rules")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getAllRules() {
        return detectionService.getAllRules();
    }

    @PostMapping("/detection-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public Object createRule(@Valid @RequestBody DetectionRuleRequest request) {
        return detectionService.createRule(request, currentUserService.requireCurrentUser());
    }

    @PutMapping("/detection-rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Object updateRule(@PathVariable UUID id, @Valid @RequestBody DetectionRuleRequest request) {
        return detectionService.updateRule(id, request, currentUserService.requireCurrentUser());
    }

    @GetMapping("/detection-findings")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getAllFindings() {
        return detectionService.getAllFindings();
    }

    @GetMapping("/detection-findings/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public Object getFinding(@PathVariable UUID id) {
        return detectionService.getFinding(id);
    }

    @PutMapping("/detection-findings/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Object updateFindingStatus(@PathVariable UUID id, @Valid @RequestBody DetectionFindingStatusRequest request) {
        return detectionService.updateFindingStatus(id, request, currentUserService.requireCurrentUser());
    }

    @GetMapping("/ai-analysis-runs")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getAllAiRuns() {
        return detectionService.getAllAiRuns();
    }

    @PostMapping("/ai-analysis-runs")
    @PreAuthorize("hasRole('ADMIN')")
    public Object createAiRun(@Valid @RequestBody AIAnalysisRunRequest request) {
        return detectionService.createAiRun(request, currentUserService.requireCurrentUser());
    }
}
