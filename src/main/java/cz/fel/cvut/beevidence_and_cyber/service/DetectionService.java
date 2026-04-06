package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.AIAnalysisRun;
import cz.fel.cvut.beevidence_and_cyber.dao.DetectionFinding;
import cz.fel.cvut.beevidence_and_cyber.dao.DetectionRule;
import cz.fel.cvut.beevidence_and_cyber.dao.DeviceLogEntry;
import cz.fel.cvut.beevidence_and_cyber.dao.DeviceSnapshot;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dao.FileSystemEvent;
import cz.fel.cvut.beevidence_and_cyber.dao.NetworkInterface;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.AIAnalysisRunDto;
import cz.fel.cvut.beevidence_and_cyber.dto.AIAnalysisRunRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionFindingDto;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionFindingStatusRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionRuleDto;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionRuleRequest;
import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.DetectionSourceTypeEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.FindingStatusEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.SeverityLevelEnum;
import cz.fel.cvut.beevidence_and_cyber.exception.NotFoundException;
import cz.fel.cvut.beevidence_and_cyber.repository.AIAnalysisRunRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionFindingRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionRuleRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceLogEntryRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceSnapshotRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.FileSystemEventRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.NetworkInterfaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DetectionService {

    private static final Duration FINDING_MERGE_WINDOW = Duration.ofHours(12);
    private static final Duration FINDING_CONTEXT_WINDOW = Duration.ofMinutes(10);
    private static final int CONTEXT_ITEM_LIMIT = 20;

    private final DetectionRuleRepository detectionRuleRepository;
    private final DetectionFindingRepository detectionFindingRepository;
    private final AIAnalysisRunRepository aiAnalysisRunRepository;
    private final DeviceLogEntryRepository deviceLogEntryRepository;
    private final FileSystemEventRepository fileSystemEventRepository;
    private final DeviceSnapshotRepository deviceSnapshotRepository;
    private final NetworkInterfaceRepository networkInterfaceRepository;
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
        return detectionFindingRepository.findAll().stream()
                .sorted(Comparator.comparing(DetectionFinding::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(apiMapper::toDto)
                .toList();
    }

    public DetectionFindingDto getFinding(UUID id) {
        return apiMapper.toDto(findFinding(id));
    }

    @Transactional
    public DetectionFindingDto updateFindingStatus(UUID id, DetectionFindingStatusRequest request, User actor) {
        DetectionFinding finding = findFinding(id);
        finding.setStatus(FindingStatusEnum.valueOf(request.status().toUpperCase(Locale.ROOT)));
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

    @Transactional
    public void evaluateSnapshotChanges(EndpointDevice device,
                                        DeviceSnapshot previousSnapshot,
                                        List<NetworkInterface> previousInterfaces,
                                        DeviceSnapshot currentSnapshot,
                                        List<NetworkInterface> currentInterfaces) {
        if (previousSnapshot == null || currentSnapshot == null || previousSnapshot.getId().equals(currentSnapshot.getId())) {
            return;
        }

        DetectionRule addedRule = findEnabledRule("NETWORK_INTERFACE_ADDED").orElse(null);
        DetectionRule removedRule = findEnabledRule("NETWORK_INTERFACE_REMOVED").orElse(null);

        Map<String, NetworkInterface> previousBySignature = previousInterfaces.stream()
                .collect(Collectors.toMap(this::networkSignature, item -> item, (left, right) -> left, LinkedHashMap::new));
        Map<String, NetworkInterface> currentBySignature = currentInterfaces.stream()
                .collect(Collectors.toMap(this::networkSignature, item -> item, (left, right) -> left, LinkedHashMap::new));

        List<NetworkInterface> addedInterfaces = currentInterfaces.stream()
                .filter(item -> !previousBySignature.containsKey(networkSignature(item)))
                .toList();
        List<NetworkInterface> removedInterfaces = previousInterfaces.stream()
                .filter(item -> !currentBySignature.containsKey(networkSignature(item)))
                .toList();

        if (addedRule != null && !addedInterfaces.isEmpty()) {
            List<Map<String, Object>> addedInterfaceMaps = addedInterfaces.stream()
                    .map(this::toNetworkInterfaceContext)
                    .toList();
            String interfaceSummary = addedInterfaces.stream()
                    .map(item -> firstNonBlank(item.getDisplayName(), item.getName(), item.getIpv4(), item.getMacAddress()))
                    .collect(Collectors.joining(", "));

            createOrRefreshFinding(
                    device,
                    addedRule,
                    "Přidáno síťové rozhraní",
                    "Na zařízení bylo detekováno nové síťové rozhraní: " + interfaceSummary,
                    currentSnapshot.getCollectedAt(),
                    buildSnapshotContext(device, previousSnapshot, currentSnapshot, addedInterfaceMaps, List.of())
            );
        }

        if (removedRule != null && !removedInterfaces.isEmpty()) {
            List<Map<String, Object>> removedInterfaceMaps = removedInterfaces.stream()
                    .map(this::toNetworkInterfaceContext)
                    .toList();
            String interfaceSummary = removedInterfaces.stream()
                    .map(item -> firstNonBlank(item.getDisplayName(), item.getName(), item.getIpv4(), item.getMacAddress()))
                    .collect(Collectors.joining(", "));

            createOrRefreshFinding(
                    device,
                    removedRule,
                    "Odebráno síťové rozhraní",
                    "Na zařízení bylo detekováno odebrané síťové rozhraní: " + interfaceSummary,
                    currentSnapshot.getCollectedAt(),
                    buildSnapshotContext(device, previousSnapshot, currentSnapshot, List.of(), removedInterfaceMaps)
            );
        }
    }

    @Transactional
    public void evaluateCollectedSignals(EndpointDevice device,
                                         List<DeviceLogEntry> logEntries,
                                         List<FileSystemEvent> fileSystemEvents) {
        if ((logEntries == null || logEntries.isEmpty()) && (fileSystemEvents == null || fileSystemEvents.isEmpty())) {
            return;
        }

        evaluateFileRules(device, fileSystemEvents == null ? List.of() : fileSystemEvents);
        evaluateLogRules(device, logEntries == null ? List.of() : logEntries);
    }

    private void evaluateFileRules(EndpointDevice device, List<FileSystemEvent> fileEvents) {
        if (fileEvents.isEmpty()) {
            return;
        }

        DetectionRule taskRule = findEnabledRule("TASK_FILE_CHANGED").orElse(null);
        DetectionRule hostsRule = findEnabledRule("HOSTS_FILE_CHANGED").orElse(null);
        DetectionRule startupRule = findEnabledRule("STARTUP_PERSISTENCE_CHANGED").orElse(null);
        DetectionRule genericRule = findEnabledRule("MONITORED_PATH_CHANGED").orElse(null);

        for (FileSystemEvent event : fileEvents) {
            String normalizedPath = normalizePath(event.getPath());
            boolean specificMatched = false;

            if (taskRule != null && isTaskPath(normalizedPath)) {
                specificMatched = true;
                createOrRefreshFinding(
                        device,
                        taskRule,
                        "Změna naplánované úlohy",
                        "V systému byla zaznamenána změna souboru naplánované úlohy: " + safeValue(event.getPath()),
                        event.getOccurredAt(),
                        buildFileEventContext(device, event)
                );
            }

            if (hostsRule != null && normalizedPath.endsWith("\\windows\\system32\\drivers\\etc\\hosts")) {
                specificMatched = true;
                createOrRefreshFinding(
                        device,
                        hostsRule,
                        "Změna souboru hosts",
                        "Byla zaznamenána změna systémového souboru hosts: " + safeValue(event.getPath()),
                        event.getOccurredAt(),
                        buildFileEventContext(device, event)
                );
            }

            if (startupRule != null && normalizedPath.contains("\\programdata\\microsoft\\windows\\start menu\\programs\\startup")) {
                specificMatched = true;
                createOrRefreshFinding(
                        device,
                        startupRule,
                        "Změna ve Startup složce",
                        "Ve veřejné Startup složce došlo ke změně: " + safeValue(event.getPath()),
                        event.getOccurredAt(),
                        buildFileEventContext(device, event)
                );
            }

            if (!specificMatched && genericRule != null) {
                createOrRefreshFinding(
                        device,
                        genericRule,
                        "Změna v monitorované cestě",
                        "Byla zaznamenána změna v monitorované cestě: " + safeValue(event.getPath()),
                        event.getOccurredAt(),
                        buildFileEventContext(device, event)
                );
            }
        }
    }

    private void evaluateLogRules(EndpointDevice device, List<DeviceLogEntry> logEntries) {
        if (logEntries.isEmpty()) {
            return;
        }

        DetectionRule localUserRule = findEnabledRule("LOCAL_USER_CREATED").orElse(null);
        DetectionRule localUserDeletedRule = findEnabledRule("LOCAL_USER_DELETED").orElse(null);
        DetectionRule powershellRule = findEnabledRule("ELEVATED_POWERSHELL_PROCESS").orElse(null);
        DetectionRule failedBurstRule = findEnabledRule("FAILED_LOGON_BURST").orElse(null);
        DetectionRule rdpRule = findEnabledRule("RDP_LOGON").orElse(null);
        DetectionRule serviceRule = findEnabledRule("SERVICE_INSTALLED").orElse(null);

        for (DeviceLogEntry logEntry : logEntries) {
            String eventCode = safeValue(logEntry.getEventCode());
            Map<String, String> parsedPayload = parseEventXml(logEntry.getRawPayload());

            if (localUserRule != null && "4720".equals(eventCode)) {
                createOrRefreshFinding(
                        device,
                        localUserRule,
                        "Přidání uživatele na PC",
                        "Byl vytvořen nový účet: " + userLabel(parsedPayload, "TargetDomainName", "TargetUserName"),
                        logEntry.getOccurredAt(),
                        buildLogContext(device, logEntry, parsedPayload)
                );
            }

            if (localUserDeletedRule != null && "4726".equals(eventCode)) {
                createOrRefreshFinding(
                        device,
                        localUserDeletedRule,
                        "Smazání uživatele na PC",
                        "Byl smazán účet: " + userLabel(parsedPayload, "TargetDomainName", "TargetUserName"),
                        logEntry.getOccurredAt(),
                        buildLogContext(device, logEntry, parsedPayload)
                );
            }

            if (rdpRule != null && "4624".equals(eventCode) && "10".equals(parsedPayload.get("LogonType"))) {
                createOrRefreshFinding(
                        device,
                        rdpRule,
                        "Nové RDP přihlášení",
                        "Proběhlo vzdálené RDP přihlášení uživatele " +
                                userLabel(parsedPayload, "TargetDomainName", "TargetUserName") +
                                detailSuffix("IP " + safeValue(parsedPayload.get("IpAddress"))),
                        logEntry.getOccurredAt(),
                        buildLogContext(device, logEntry, parsedPayload)
                );
            }

            if (serviceRule != null && ("4697".equals(eventCode) || "7045".equals(eventCode))) {
                createOrRefreshFinding(
                        device,
                        serviceRule,
                        "Instalace nové služby",
                        "Byla zaznamenána instalace služby " +
                                safeValue(firstNonBlank(parsedPayload.get("ServiceName"), parsedPayload.get("Service File Name"), parsedPayload.get("ImagePath"))),
                        logEntry.getOccurredAt(),
                        buildLogContext(device, logEntry, parsedPayload)
                );
            }

            if (powershellRule != null && isElevatedPowerShell(device, logEntry, parsedPayload)) {
                createOrRefreshFinding(
                        device,
                        powershellRule,
                        "Spuštění PowerShellu s elevovanými právy",
                        logEntry.getMessage(),
                        logEntry.getOccurredAt(),
                        buildLogContext(device, logEntry, parsedPayload)
                );
            }

            if (failedBurstRule != null && "4625".equals(eventCode)) {
                evaluateFailedLogonBurst(device, failedBurstRule, logEntry, parsedPayload);
            }
        }
    }

    private void evaluateFailedLogonBurst(EndpointDevice device,
                                          DetectionRule rule,
                                          DeviceLogEntry triggerEntry,
                                          Map<String, String> parsedPayload) {
        LocalDateTime occurredAt = triggerEntry.getOccurredAt() == null ? LocalDateTime.now() : triggerEntry.getOccurredAt();
        LocalDateTime from = occurredAt.minusMinutes(10);
        List<DeviceLogEntry> surroundingLogs = deviceLogEntryRepository.findByDeviceAndOccurredAtBetweenOrderByOccurredAtAsc(device, from, occurredAt);
        String targetUser = userLabel(parsedPayload, "TargetDomainName", "TargetUserName");
        String ipAddress = safeValue(parsedPayload.get("IpAddress"));

        long matchingCount = surroundingLogs.stream()
                .filter(item -> "4625".equals(item.getEventCode()))
                .filter(item -> {
                    Map<String, String> itemPayload = parseEventXml(item.getRawPayload());
                    return Objects.equals(targetUser, userLabel(itemPayload, "TargetDomainName", "TargetUserName"))
                            && Objects.equals(ipAddress, safeValue(itemPayload.get("IpAddress")));
                })
                .count();

        if (matchingCount < 5) {
            return;
        }

        createOrRefreshFinding(
                device,
                rule,
                "Opakované neúspěšné přihlášení",
                "Bylo zjištěno alespoň " + matchingCount + " neúspěšných přihlášení pro " + targetUser +
                        detailSuffix("IP " + ipAddress),
                occurredAt,
                buildLogContext(device, triggerEntry, parsedPayload)
        );
    }

    private boolean isElevatedPowerShell(EndpointDevice device, DeviceLogEntry logEntry, Map<String, String> parsedPayload) {
        String eventCode = safeValue(logEntry.getEventCode());
        if ("4688".equals(eventCode)) {
            String processName = normalizePath(firstNonBlank(parsedPayload.get("NewProcessName"), parsedPayload.get("ProcessName")));
            if (!(processName.endsWith("\\powershell.exe") || processName.endsWith("\\pwsh.exe"))) {
                return false;
            }
            String tokenElevation = safeValue(parsedPayload.get("TokenElevationType"));
            return !tokenElevation.equals("-") && !tokenElevation.equals("%%1938");
        }

        if ("4103".equals(eventCode) || "4104".equals(eventCode)) {
            String rawPayload = safeValue(logEntry.getRawPayload()).toLowerCase(Locale.ROOT);
            return rawPayload.contains("powershell") || rawPayload.contains("pwsh");
        }

        if ("400".equals(eventCode) || "40961".equals(eventCode)) {
            String rawPayload = safeValue(logEntry.getRawPayload()).toLowerCase(Locale.ROOT);
            return rawPayload.contains("powershell")
                    && (hasPrivilegedSecurityContext(rawPayload) || hasRecentPrivilegedLogon(device, logEntry.getOccurredAt()));
        }

        return false;
    }

    private boolean hasPrivilegedSecurityContext(String rawPayload) {
        return rawPayload.contains("userid='s-1-5-18'")
                || rawPayload.contains("userid=\"s-1-5-18\"")
                || rawPayload.contains("userid='s-1-5-19'")
                || rawPayload.contains("userid=\"s-1-5-19\"")
                || rawPayload.contains("userid='s-1-5-20'")
                || rawPayload.contains("userid=\"s-1-5-20\"");
    }

    private boolean hasRecentPrivilegedLogon(EndpointDevice device, LocalDateTime occurredAt) {
        if (device == null || occurredAt == null) {
            return false;
        }
        LocalDateTime from = occurredAt.minusMinutes(5);
        LocalDateTime to = occurredAt.plusMinutes(1);
        return deviceLogEntryRepository.findByDeviceAndOccurredAtBetweenOrderByOccurredAtAsc(device, from, to).stream()
                .anyMatch(item -> "4672".equals(item.getEventCode()));
    }

    private DetectionFinding createOrRefreshFinding(EndpointDevice device,
                                                    DetectionRule rule,
                                                    String title,
                                                    String description,
                                                    LocalDateTime occurredAt,
                                                    Map<String, Object> contextJson) {
        LocalDateTime effectiveTime = occurredAt == null ? LocalDateTime.now() : occurredAt;
        DetectionFinding existing = detectionFindingRepository.findTop10ByDeviceAndRuleOrderByLastSeenAtDesc(device, rule).stream()
                .filter(item -> title.equals(item.getTitle()))
                .filter(item -> item.getStatus() == FindingStatusEnum.OPEN || item.getStatus() == FindingStatusEnum.ACKNOWLEDGED)
                .filter(item -> item.getLastSeenAt() != null && !item.getLastSeenAt().isBefore(effectiveTime.minus(FINDING_MERGE_WINDOW)))
                .findFirst()
                .orElse(null);

        DetectionFinding finding = existing == null ? new DetectionFinding() : existing;
        finding.setDevice(device);
        finding.setRule(rule);
        finding.setSeverity(rule.getSeverity());
        finding.setTitle(title);
        finding.setDescription(description);
        finding.setLastSeenAt(effectiveTime);
        finding.setContextJson(contextJson);
        finding.setCreatedByAi(false);
        if (existing == null) {
            finding.setStatus(FindingStatusEnum.OPEN);
            finding.setFirstSeenAt(effectiveTime);
        }

        return detectionFindingRepository.save(finding);
    }

    private Map<String, Object> buildLogContext(EndpointDevice device,
                                                DeviceLogEntry triggerEntry,
                                                Map<String, String> parsedPayload) {
        LocalDateTime center = triggerEntry.getOccurredAt() == null ? LocalDateTime.now() : triggerEntry.getOccurredAt();
        LocalDateTime from = center.minus(FINDING_CONTEXT_WINDOW);
        LocalDateTime to = center.plus(FINDING_CONTEXT_WINDOW);
        List<DeviceLogEntry> relatedLogs = deviceLogEntryRepository.findByDeviceAndOccurredAtBetweenOrderByOccurredAtAsc(device, from, to);
        List<FileSystemEvent> relatedFileEvents = fileSystemEventRepository.findByDeviceAndOccurredAtBetweenOrderByOccurredAtAsc(device, from, to);
        DeviceSnapshot snapshot = deviceSnapshotRepository.findTopByDeviceOrderByVersionNoDesc(device).orElse(null);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("device", buildDeviceSummary(device, snapshot));
        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("type", "LOG");
        trigger.put("occurredAt", triggerEntry.getOccurredAt());
        trigger.put("logSource", triggerEntry.getLogSource() == null ? null : triggerEntry.getLogSource().name());
        trigger.put("eventCode", triggerEntry.getEventCode());
        trigger.put("level", triggerEntry.getLevel());
        trigger.put("message", triggerEntry.getMessage());
        trigger.put("parsedPayload", parsedPayload);
        context.put("trigger", trigger);
        Map<String, Object> timeWindow = new LinkedHashMap<>();
        timeWindow.put("from", from);
        timeWindow.put("to", to);
        context.put("timeWindow", timeWindow);
        context.put("relatedLogs", relatedLogs.stream().limit(CONTEXT_ITEM_LIMIT).map(this::toLogContext).toList());
        context.put("relatedFileEvents", relatedFileEvents.stream().limit(CONTEXT_ITEM_LIMIT).map(this::toFileEventContext).toList());
        return context;
    }

    private Map<String, Object> buildFileEventContext(EndpointDevice device, FileSystemEvent triggerEvent) {
        LocalDateTime center = triggerEvent.getOccurredAt() == null ? LocalDateTime.now() : triggerEvent.getOccurredAt();
        LocalDateTime from = center.minus(FINDING_CONTEXT_WINDOW);
        LocalDateTime to = center.plus(FINDING_CONTEXT_WINDOW);
        List<DeviceLogEntry> relatedLogs = deviceLogEntryRepository.findByDeviceAndOccurredAtBetweenOrderByOccurredAtAsc(device, from, to);
        List<FileSystemEvent> relatedFileEvents = fileSystemEventRepository.findByDeviceAndOccurredAtBetweenOrderByOccurredAtAsc(device, from, to);
        DeviceSnapshot snapshot = deviceSnapshotRepository.findTopByDeviceOrderByVersionNoDesc(device).orElse(null);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("device", buildDeviceSummary(device, snapshot));
        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("type", "FILE");
        trigger.put("occurredAt", triggerEvent.getOccurredAt());
        trigger.put("eventType", triggerEvent.getEventType() == null ? null : triggerEvent.getEventType().name());
        trigger.put("path", triggerEvent.getPath());
        trigger.put("actorUsername", triggerEvent.getActorUsername());
        trigger.put("sourceLog", triggerEvent.getSourceLog());
        trigger.put("detailsJson", triggerEvent.getDetailsJson());
        context.put("trigger", trigger);
        Map<String, Object> timeWindow = new LinkedHashMap<>();
        timeWindow.put("from", from);
        timeWindow.put("to", to);
        context.put("timeWindow", timeWindow);
        context.put("relatedLogs", relatedLogs.stream().limit(CONTEXT_ITEM_LIMIT).map(this::toLogContext).toList());
        context.put("relatedFileEvents", relatedFileEvents.stream().limit(CONTEXT_ITEM_LIMIT).map(this::toFileEventContext).toList());
        return context;
    }

    private Map<String, Object> buildSnapshotContext(EndpointDevice device,
                                                     DeviceSnapshot previousSnapshot,
                                                     DeviceSnapshot currentSnapshot,
                                                     List<Map<String, Object>> addedInterfaces,
                                                     List<Map<String, Object>> removedInterfaces) {
        LocalDateTime center = currentSnapshot.getCollectedAt() == null ? LocalDateTime.now() : currentSnapshot.getCollectedAt();
        LocalDateTime from = center.minus(FINDING_CONTEXT_WINDOW);
        LocalDateTime to = center.plus(FINDING_CONTEXT_WINDOW);
        List<DeviceLogEntry> relatedLogs = deviceLogEntryRepository.findByDeviceAndOccurredAtBetweenOrderByOccurredAtAsc(device, from, to);
        List<FileSystemEvent> relatedFileEvents = fileSystemEventRepository.findByDeviceAndOccurredAtBetweenOrderByOccurredAtAsc(device, from, to);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("device", buildDeviceSummary(device, currentSnapshot));
        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("type", "SNAPSHOT");
        trigger.put("occurredAt", currentSnapshot.getCollectedAt());
        trigger.put("previousSnapshotVersion", previousSnapshot.getVersionNo());
        trigger.put("currentSnapshotVersion", currentSnapshot.getVersionNo());
        trigger.put("addedNetworkInterfaces", addedInterfaces);
        trigger.put("removedNetworkInterfaces", removedInterfaces);
        context.put("trigger", trigger);
        context.put("previousSnapshot", buildSnapshotSummary(previousSnapshot));
        context.put("currentSnapshot", buildSnapshotSummary(currentSnapshot));
        Map<String, Object> timeWindow = new LinkedHashMap<>();
        timeWindow.put("from", from);
        timeWindow.put("to", to);
        context.put("timeWindow", timeWindow);
        context.put("relatedLogs", relatedLogs.stream().limit(CONTEXT_ITEM_LIMIT).map(this::toLogContext).toList());
        context.put("relatedFileEvents", relatedFileEvents.stream().limit(CONTEXT_ITEM_LIMIT).map(this::toFileEventContext).toList());
        return context;
    }

    private Map<String, Object> buildDeviceSummary(EndpointDevice device, DeviceSnapshot snapshot) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("deviceId", device.getId());
        summary.put("hostname", device.getHostname());
        summary.put("fqdn", device.getFqdn());
        summary.put("primaryIp", device.getPrimaryIp());
        summary.put("status", device.getStatus() == null ? null : device.getStatus().name());
        summary.put("agentInstalled", device.isAgentInstalled());
        if (snapshot != null) {
            summary.put("snapshot", buildSnapshotSummary(snapshot));
        }
        return summary;
    }

    private Map<String, Object> buildSnapshotSummary(DeviceSnapshot snapshot) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("snapshotId", snapshot.getId());
        summary.put("versionNo", snapshot.getVersionNo());
        summary.put("collectedAt", snapshot.getCollectedAt());
        summary.put("osName", snapshot.getOsName());
        summary.put("osVersion", snapshot.getOsVersion());
        summary.put("osBuild", snapshot.getOsBuild());
        summary.put("currentLoggedUser", snapshot.getCurrentLoggedUser());
        summary.put("lastBootAt", snapshot.getLastBootAt());
        List<NetworkInterface> interfaces = networkInterfaceRepository.findBySnapshot(snapshot);
        summary.put("networkInterfaces", interfaces.stream().map(this::toNetworkInterfaceContext).toList());
        return summary;
    }

    private Map<String, Object> toLogContext(DeviceLogEntry logEntry) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("occurredAt", logEntry.getOccurredAt());
        item.put("logSource", logEntry.getLogSource() == null ? null : logEntry.getLogSource().name());
        item.put("level", logEntry.getLevel());
        item.put("eventCode", logEntry.getEventCode());
        item.put("message", logEntry.getMessage());
        return item;
    }

    private Map<String, Object> toFileEventContext(FileSystemEvent event) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("occurredAt", event.getOccurredAt());
        item.put("eventType", event.getEventType() == null ? null : event.getEventType().name());
        item.put("path", event.getPath());
        item.put("actorUsername", event.getActorUsername());
        item.put("sourceLog", event.getSourceLog());
        return item;
    }

    private Map<String, Object> toNetworkInterfaceContext(NetworkInterface networkInterface) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", networkInterface.getName());
        item.put("displayName", networkInterface.getDisplayName());
        item.put("macAddress", networkInterface.getMacAddress());
        item.put("ipv4", networkInterface.getIpv4());
        item.put("ipv6", networkInterface.getIpv6());
        item.put("primary", networkInterface.isPrimary());
        item.put("up", networkInterface.isUp());
        return item;
    }

    private Optional<DetectionRule> findEnabledRule(String code) {
        return detectionRuleRepository.findByCodeIgnoreCase(code).filter(DetectionRule::isEnabled);
    }

    private DetectionFinding findFinding(UUID id) {
        return detectionFindingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Detection finding with id " + id + " not found"));
    }

    private void applyRuleRequest(DetectionRule rule, DetectionRuleRequest request) {
        rule.setCode(request.code());
        rule.setName(request.name());
        rule.setDescription(request.description());
        rule.setSeverity(SeverityLevelEnum.valueOf(request.severity().toUpperCase(Locale.ROOT)));
        rule.setSourceType(DetectionSourceTypeEnum.valueOf(request.sourceType().toUpperCase(Locale.ROOT)));
        rule.setConditionJson(request.conditionJson());
        rule.setEnabled(request.enabled());
    }

    private String networkSignature(NetworkInterface networkInterface) {
        return String.join("|",
                safeValue(networkInterface.getName()),
                safeValue(networkInterface.getDisplayName()),
                safeValue(networkInterface.getMacAddress()),
                safeValue(networkInterface.getIpv4()),
                safeValue(networkInterface.getIpv6()));
    }

    private Map<String, String> parseEventXml(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return Map.of();
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(rawPayload)));
            NodeList eventDataNodes = document.getElementsByTagName("Data");
            Map<String, String> data = new LinkedHashMap<>();
            for (int index = 0; index < eventDataNodes.getLength(); index++) {
                Node node = eventDataNodes.item(index);
                Node nameNode = node.getAttributes() == null ? null : node.getAttributes().getNamedItem("Name");
                if (nameNode == null) {
                    continue;
                }
                String key = nameNode.getNodeValue();
                String value = node.getTextContent() == null ? "" : node.getTextContent().trim();
                if (!key.isBlank() && !value.isBlank()) {
                    data.put(key, value);
                }
            }
            putIfPresent(data, "ProviderName", firstAttributeValue(document, "Provider", "Name"));
            putIfPresent(data, "Channel", firstNodeText(document, "Channel"));
            putIfPresent(data, "Computer", firstNodeText(document, "Computer"));
            putIfPresent(data, "UserId", firstAttributeValue(document, "Security", "UserID"));
            putIfPresent(data, "ProcessId", firstAttributeValue(document, "Execution", "ProcessID"));
            putIfPresent(data, "ThreadId", firstAttributeValue(document, "Execution", "ThreadID"));
            putIfPresent(data, "ActivityId", firstAttributeValue(document, "Correlation", "ActivityID"));
            putIfPresent(data, "SystemTime", firstAttributeValue(document, "TimeCreated", "SystemTime"));
            putIfPresent(data, "EventRecordID", firstNodeText(document, "EventRecordID"));
            return Map.copyOf(data);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private void putIfPresent(Map<String, String> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value.trim());
        }
    }

    private String firstNodeText(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? null : text.trim();
    }

    private String firstAttributeValue(Document document, String tagName, String attributeName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getAttributes() == null) {
            return null;
        }
        Node attribute = nodes.item(0).getAttributes().getNamedItem(attributeName);
        return attribute == null ? null : attribute.getNodeValue();
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace('/', '\\').toLowerCase(Locale.ROOT).trim();
    }

    private boolean isTaskPath(String normalizedPath) {
        return normalizedPath.contains("\\windows\\system32\\tasks\\")
                || normalizedPath.endsWith("\\windows\\system32\\tasks")
                || normalizedPath.contains("\\windows\\tasks\\")
                || normalizedPath.endsWith("\\windows\\tasks");
    }

    private String userLabel(Map<String, String> payload, String domainKey, String userKey) {
        String user = safeValue(payload.get(userKey));
        String domain = safeValue(payload.get(domainKey));
        if (user.equals("-")) {
            return "-";
        }
        if (domain.equals("-")) {
            return user;
        }
        return domain + "\\" + user;
    }

    private String detailSuffix(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return "";
        }
        return " | " + value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "-";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "-";
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
