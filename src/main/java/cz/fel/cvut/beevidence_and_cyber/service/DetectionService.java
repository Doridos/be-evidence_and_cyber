package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.AIAnalysisRun;
import cz.fel.cvut.beevidence_and_cyber.dao.DetectionFinding;
import cz.fel.cvut.beevidence_and_cyber.dao.DetectionRule;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.*;
import cz.fel.cvut.beevidence_and_cyber.enumeration.*;
import cz.fel.cvut.beevidence_and_cyber.exception.NotFoundException;
import cz.fel.cvut.beevidence_and_cyber.repository.AIAnalysisRunRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionFindingRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DetectionService {

    private final DetectionRuleRepository detectionRuleRepository;
    private final DetectionFindingRepository detectionFindingRepository;
    private final AIAnalysisRunRepository aiAnalysisRunRepository;
    private final DeviceService deviceService;
    private final ApiMapper apiMapper;
    private final AuditService auditService;

    public List<DetectionRuleDto> getAllRules() {
        return detectionRuleRepository.findAll().stream().map(apiMapper::toDto).toList();
    }

    @Transactional
    public DetectionRuleDto createRule(DetectionRuleRequest request, User actor) {
        DetectionRule rule = new DetectionRule();
        applyRuleRequest(rule, request);
        DetectionRule saved = detectionRuleRepository.save(rule);
        auditService.log(actor, ActorSourceEnum.WEB, "CREATE_DETECTION_RULE", "DETECTION_RULE", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("code", saved.getCode()));
        return apiMapper.toDto(saved);
    }

    @Transactional
    public DetectionRuleDto updateRule(UUID id, DetectionRuleRequest request, User actor) {
        DetectionRule rule = detectionRuleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Detection rule with id " + id + " not found"));
        applyRuleRequest(rule, request);
        DetectionRule saved = detectionRuleRepository.save(rule);
        auditService.log(actor, ActorSourceEnum.WEB, "UPDATE_DETECTION_RULE", "DETECTION_RULE", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("code", saved.getCode()));
        return apiMapper.toDto(saved);
    }

    public List<DetectionFindingDto> getAllFindings() {
        return detectionFindingRepository.findAll().stream().map(apiMapper::toDto).toList();
    }

    public DetectionFindingDto getFinding(UUID id) {
        return apiMapper.toDto(findFinding(id));
    }

    @Transactional
    public DetectionFindingDto updateFindingStatus(UUID id, DetectionFindingStatusRequest request, User actor) {
        DetectionFinding finding = findFinding(id);
        finding.setStatus(FindingStatusEnum.valueOf(request.status().toUpperCase()));
        finding.setLastSeenAt(LocalDateTime.now());
        DetectionFinding saved = detectionFindingRepository.save(finding);
        auditService.log(actor, ActorSourceEnum.WEB, "UPDATE_FINDING_STATUS", "DETECTION_FINDING", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("status", saved.getStatus().name()));
        return apiMapper.toDto(saved);
    }

    public List<AIAnalysisRunDto> getAllAiRuns() {
        return aiAnalysisRunRepository.findAll().stream().map(apiMapper::toDto).toList();
    }

    @Transactional
    public AIAnalysisRunDto createAiRun(AIAnalysisRunRequest request, User actor) {
        EndpointDevice device = deviceService.findDevice(request.deviceId());
        AIAnalysisRun run = new AIAnalysisRun();
        run.setDevice(device);
        run.setTriggeredByUser(actor);
        run.setModelName(request.modelName());
        run.setPromptVersion(request.promptVersion());
        run.setStartedAt(LocalDateTime.now());
        run.setCompletedAt(LocalDateTime.now());
        run.setResultSummary(request.resultSummary());
        run.setRiskScore(BigDecimal.ZERO);
        AIAnalysisRun saved = aiAnalysisRunRepository.save(run);
        auditService.log(actor, ActorSourceEnum.WEB, "CREATE_AI_ANALYSIS_RUN", "AI_ANALYSIS_RUN", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("deviceId", device.getId().toString()));
        return apiMapper.toDto(saved);
    }

    private DetectionFinding findFinding(UUID id) {
        return detectionFindingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Detection finding with id " + id + " not found"));
    }

    private void applyRuleRequest(DetectionRule rule, DetectionRuleRequest request) {
        rule.setCode(request.code());
        rule.setName(request.name());
        rule.setDescription(request.description());
        rule.setSeverity(SeverityLevelEnum.valueOf(request.severity().toUpperCase()));
        rule.setSourceType(DetectionSourceTypeEnum.valueOf(request.sourceType().toUpperCase()));
        rule.setConditionJson(request.conditionJson());
        rule.setEnabled(request.enabled());
    }
}
