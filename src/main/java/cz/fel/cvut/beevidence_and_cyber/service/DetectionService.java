package cz.fel.cvut.beevidence_and_cyber.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.fel.cvut.beevidence_and_cyber.dao.DetectionFinding;
import cz.fel.cvut.beevidence_and_cyber.dao.DetectionFindingEvent;
import cz.fel.cvut.beevidence_and_cyber.dao.DetectionRule;
import cz.fel.cvut.beevidence_and_cyber.dao.DeviceLogEntry;
import cz.fel.cvut.beevidence_and_cyber.dao.DeviceSnapshot;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dao.FileSystemEvent;
import cz.fel.cvut.beevidence_and_cyber.dao.NetworkInterface;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionFindingDto;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionFindingEventDto;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionFindingStatusRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionRuleDto;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionRuleRequest;
import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.DetectionSourceTypeEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.DetectionFindingEventTypeEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.FindingStatusEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.SeverityLevelEnum;
import cz.fel.cvut.beevidence_and_cyber.exception.NotFoundException;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionFindingEventRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionFindingRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionRuleRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceLogEntryRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceSnapshotRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.FileSystemEventRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.NetworkInterfaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Duration;
import java.time.LocalDateTime;
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
@Slf4j
@RequiredArgsConstructor
public class DetectionService {

    private static final Duration FINDING_CONTEXT_WINDOW = Duration.ofMinutes(10);
    private static final Duration USB_FINDING_MERGE_WINDOW = Duration.ofMinutes(5);
    private static final int CONTEXT_ITEM_LIMIT = 500;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DetectionRuleRepository detectionRuleRepository;
    private final DetectionFindingRepository detectionFindingRepository;
    private final DetectionFindingEventRepository detectionFindingEventRepository;
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
                .map(finding -> apiMapper.toDto(finding, List.of()))
                .toList();
    }

    public DetectionFindingDto getFinding(UUID id) {
        DetectionFinding finding = findFinding(id);
        List<DetectionFindingEventDto> events = detectionFindingEventRepository.findByFindingOrderByOccurredAtAscIdAsc(finding).stream()
                .map(apiMapper::toDto)
                .toList();
        return apiMapper.toDto(finding, events);
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
        DetectionRule osUpdatedRule = findEnabledRule("OS_UPDATED").orElse(null);

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

        if (osUpdatedRule != null && hasOperatingSystemChanged(previousSnapshot, currentSnapshot)) {
            createOrRefreshFinding(
                    device,
                    osUpdatedRule,
                    "Aktualizace operačního systému",
                    "Na zařízení byla detekována změna operačního systému z " +
                            describeOperatingSystem(previousSnapshot) + " na " + describeOperatingSystem(currentSnapshot),
                    currentSnapshot.getCollectedAt(),
                    buildOsUpdateContext(device, previousSnapshot, currentSnapshot)
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
            if (isBenignSystemTaskPath(normalizedPath)) {
                continue;
            }
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
        DetectionRule usbConnectedRule = findEnabledRule("USB_DEVICE_CONNECTED").orElse(null);
        DetectionRule usbBlockedAttemptRule = findEnabledRule("USB_BLOCKED_CONNECTION_ATTEMPT").orElse(null);
        long processCreateEvents = logEntries.stream()
                .filter(logEntry -> "4688".equals(safeValue(logEntry.getEventCode())))
                .count();
        long powerShellProcessCandidates = logEntries.stream()
                .filter(logEntry -> "POWERSHELL_ELEVATED_LIVE".equalsIgnoreCase(safeValue(logEntry.getEventCode()))
                        || isPowerShellProcessCreationLog(logEntry, parseEventXml(logEntry.getRawPayload())))
                .count();
        log.info(
                "Evaluating collected log signals. deviceHostname={}, logEntries={}, processCreateEvents={}, powerShellProcessCandidates={}, usbConnectedRuleEnabled={}, usbBlockedRuleEnabled={}, usbBlockedOnDevice={}",
                device.getHostname(),
                logEntries.size(),
                processCreateEvents,
                powerShellProcessCandidates,
                usbConnectedRule != null,
                usbBlockedAttemptRule != null,
                device.isUsbRemovableBlocked()
        );
        if (powershellRule != null && powerShellProcessCandidates == 0) {
            log.info(
                    "No PowerShell process creation candidates found in current batch. deviceHostname={}, logEntries={}",
                    device.getHostname(),
                    logEntries.size()
            );
        }

        for (DeviceLogEntry logEntry : logEntries) {
            String eventCode = safeValue(logEntry.getEventCode());
            Map<String, String> parsedPayload = parseEventXml(logEntry.getRawPayload());

            if ("POWERSHELL".equalsIgnoreCase(safeValue(logEntry.getLogSource() == null ? null : logEntry.getLogSource().name()))
                    && ("400".equals(eventCode) || "40961".equals(eventCode) || "4103".equals(eventCode) || "4104".equals(eventCode))) {
                continue;
            }

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

            UsbConnectionAssessment usbAssessment = assessUsbConnectionEvent(logEntry, parsedPayload);
            if (isUsbCandidateLogEntry(logEntry, parsedPayload)) {
                log.info(
                        "USB candidate log evaluated. deviceHostname={}, blocked={}, logSource={}, eventCode={}, accepted={}, storageLikely={}, deviceType={}, reason={}, message={}",
                        device.getHostname(),
                        device.isUsbRemovableBlocked(),
                        logEntry.getLogSource(),
                        eventCode,
                        usbAssessment.accepted(),
                        usbAssessment.storageLikely(),
                        usbAssessment.deviceType(),
                        usbAssessment.reason(),
                        shorten(safeValue(logEntry.getMessage()), 180)
                );
            }
            if ((usbConnectedRule != null || usbBlockedAttemptRule != null) && usbAssessment.accepted()) {
                String usbDevice = describeUsbDevice(logEntry, parsedPayload);
                log.info(
                        "USB event matched. deviceHostname={}, blocked={}, storageLikely={}, deviceType={}, reason={}, eventCode={}, usbDevice={}",
                        device.getHostname(),
                        device.isUsbRemovableBlocked(),
                        usbAssessment.storageLikely(),
                        usbAssessment.deviceType(),
                        usbAssessment.reason(),
                        eventCode,
                        usbDevice
                );
                if (device.isUsbRemovableBlocked()) {
                    // USB is blocked on this device — any detected USB event is a blocked attempt
                    DetectionRule blockedRule = firstNonNull(usbBlockedAttemptRule, usbConnectedRule);
                    if (blockedRule != null) {
                        createOrRefreshFinding(
                                device,
                                blockedRule,
                                "Pokus o připojení blokovaného USB zařízení",
                                "Byl zaznamenán pokus o připojení USB zařízení na stanici s aktivní blokací USB: " + usbDevice,
                                logEntry.getOccurredAt(),
                                buildLogContext(device, logEntry, parsedPayload, usbAssessment)
                        );
                    }
                } else if (usbAssessment.storageLikely()) {
                    // USB not blocked and confirmed mass storage — fire USB_DEVICE_CONNECTED
                    if (usbConnectedRule != null) {
                        createOrRefreshFinding(
                                device,
                                usbConnectedRule,
                                "Připojeno USB mass storage zařízení",
                                "Bylo zaznamenáno připojení USB mass storage zařízení: " + usbDevice,
                                logEntry.getOccurredAt(),
                                buildLogContext(device, logEntry, parsedPayload, usbAssessment)
                        );
                    }
                }
                // If not blocked and storageLikely=false (generic USB\VID_ from non-blocked device):
                // skip — would be a false positive (keyboard, mouse, etc.)
            }

            ElevatedPowerShellAssessment powerShellAssessment = assessElevatedPowerShell(device, logEntry, parsedPayload);
            if (powershellRule != null && powerShellAssessment.candidate()) {
                log.info(
                        "Elevated PowerShell evaluated. deviceHostname={}, eventCode={}, accepted={}, reason={}, processName={}, tokenElevationType={}, user={}, message={}",
                        device.getHostname(),
                        eventCode,
                        powerShellAssessment.accepted(),
                        powerShellAssessment.reason(),
                        shorten(powerShellAssessment.processName(), 140),
                        powerShellAssessment.tokenElevationType(),
                        powerShellAssessment.userLabel(),
                        shorten(safeValue(logEntry.getMessage()), 180)
                );
            }
            if (powershellRule != null && powerShellAssessment.accepted()) {
                createOrRefreshFinding(
                        device,
                        powershellRule,
                        "Spuštění PowerShellu s elevovanými právy",
                        buildPowerShellFindingDescription(logEntry, parsedPayload),
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

    private ElevatedPowerShellAssessment assessElevatedPowerShell(EndpointDevice device, DeviceLogEntry logEntry, Map<String, String> parsedPayload) {
        String eventCode = safeValue(logEntry.getEventCode());
        if ("POWERSHELL_ELEVATED_LIVE".equalsIgnoreCase(eventCode)) {
            String processName = normalizePath(firstNonBlank(
                    parsedPayload.get("processName"),
                    parsedPayload.get("NewProcessName"),
                    parsedPayload.get("ProcessName")
            ));
            String subjectUser = firstNonBlank(
                    parsedPayload.get("user"),
                    parsedPayload.get("subjectUser"),
                    userLabel(parsedPayload, "SubjectDomainName", "SubjectUserName")
            );
            String tokenElevation = safeValue(parsedPayload.get("tokenElevationType"));
            if (!isPowerShellExecutable(processName)) {
                return new ElevatedPowerShellAssessment(true, false, "live event is not powershell", processName, tokenElevation, safeValue(subjectUser));
            }
            if ("-".equals(safeValue(subjectUser))) {
                return new ElevatedPowerShellAssessment(true, false, "live event user missing", processName, tokenElevation, "-");
            }
            if (isBuiltinSecurityPrincipal(subjectUser)) {
                return new ElevatedPowerShellAssessment(true, false, "builtin security principal", processName, tokenElevation, subjectUser);
            }
            return new ElevatedPowerShellAssessment(true, true, "agent live elevated powershell event", processName, tokenElevation, subjectUser);
        }

        if (!"4688".equals(eventCode)) {
            return new ElevatedPowerShellAssessment(false, false, "not a process creation event", "-", "-", "-");
        }

        String processName = normalizePath(firstNonBlank(parsedPayload.get("NewProcessName"), parsedPayload.get("ProcessName")));
        if (!isPowerShellExecutable(processName)) {
            return new ElevatedPowerShellAssessment(true, false, "process is not powershell", processName, "-", "-");
        }

        String tokenElevation = safeValue(parsedPayload.get("TokenElevationType"));
        if (!isElevatedToken(tokenElevation)) {
            return new ElevatedPowerShellAssessment(true, false, "token is not elevated", processName, tokenElevation, "-");
        }

        String subjectUser = userLabel(parsedPayload, "SubjectDomainName", "SubjectUserName");
        if ("-".equals(subjectUser)) {
            return new ElevatedPowerShellAssessment(true, false, "subject user missing", processName, tokenElevation, subjectUser);
        }
        if (isBuiltinSecurityPrincipal(subjectUser)) {
            return new ElevatedPowerShellAssessment(true, false, "builtin security principal", processName, tokenElevation, subjectUser);
        }
        return new ElevatedPowerShellAssessment(true, true, "powershell process created with elevated token", processName, tokenElevation, subjectUser);
    }

    private boolean isPowerShellExecutable(String processName) {
        return processName.endsWith("\\powershell.exe")
                || processName.endsWith("\\pwsh.exe")
                || "powershell.exe".equals(processName)
                || "pwsh.exe".equals(processName);
    }

    private boolean isElevatedToken(String tokenElevation) {
        if (tokenElevation == null || tokenElevation.isBlank() || tokenElevation.equals("-")) {
            return false;
        }
        String normalized = tokenElevation.toLowerCase(Locale.ROOT);
        return !normalized.contains("1938");
    }

    private boolean isBuiltinSecurityPrincipal(String userLabel) {
        if (userLabel == null) {
            return true;
        }
        String normalized = userLabel.toLowerCase(Locale.ROOT);
        return normalized.contains("system")
                || normalized.contains("localservice")
                || normalized.contains("networkservice")
                || normalized.endsWith("\\-")
                || normalized.equals("-");
    }

    private boolean isUsbConnectionEvent(DeviceLogEntry logEntry, Map<String, String> parsedPayload) {
        return assessUsbConnectionEvent(logEntry, parsedPayload).accepted();
    }

    /**
     * Determines whether a log entry represents a USB mass storage connection event.
     *
     * <p>Three tiers, evaluated in order:
     * <ol>
     *   <li><b>USB_STORAGE_CONNECTED</b> — emitted by the agent's {@code UsbStorageCollector}
     *       from the snapshot collector based on Get-Disk / Win32_DiskDrive / Get-PnpDevice.
     *       Accepted according to the structured payload fields carried by the agent event.</li>
     *   <li><b>Kernel-PnP event with USBSTOR\ prefix</b> — the USBSTOR driver was loaded,
     *       meaning a mass storage device was successfully enumerated.
     *       Accepted as confirmed mass storage.</li>
     *   <li><b>Kernel-PnP event with USB\VID_ prefix</b> — a generic USB device was detected
     *       by PnP but the USBSTOR driver was not loaded (e.g. blocked).
     *       Accepted as a USB device (storageLikely=false) so that the calling code can
     *       use it for the blocked-attempt path when {@code device.usbRemovableBlocked=true}.</li>
     * </ol>
     * Everything else is rejected to avoid false positives from unrelated log entries.
     */
    private UsbConnectionAssessment assessUsbConnectionEvent(DeviceLogEntry logEntry, Map<String, String> parsedPayload) {
        String eventCode = safeValue(logEntry.getEventCode());

        // Tier 1: clean agent signal from the snapshot collector
        if ("USB_STORAGE_CONNECTED".equalsIgnoreCase(eventCode)) {
            boolean storageLikely = parseBoolean(parsedPayload.get("storageLikely"), true);
            String deviceType = firstNonBlank(
                    parsedPayload.get("deviceType"),
                    storageLikely ? "mass_storage" : "usb_device"
            );
            String reason = firstNonBlank(
                    parsedPayload.get("reason"),
                    parsedPayload.get("collector"),
                    storageLikely ? "Agent USB storage event" : "Agent USB device event"
            );
            log.info(
                    "USB assessment: agent event received. storageLikely={}, deviceType={}, reason={}, detectionSources={}, stableId={}, message={}",
                    storageLikely,
                    deviceType,
                    reason,
                    safeValue(parsedPayload.get("detectionSources")),
                    safeValue(parsedPayload.get("stableId")),
                    shorten(safeValue(logEntry.getMessage()), 160)
            );
            return new UsbConnectionAssessment(true, storageLikely, deviceType, reason);
        }

        // Only evaluate known Kernel-PnP event codes
        boolean isPnpCode = List.of("400", "410", "420", "430", "20001", "20003", "2100", "2101", "2102")
                .contains(eventCode);
        if (!isPnpCode) {
            return new UsbConnectionAssessment(false, false, "unknown", "not a USB or PnP event");
        }

        // Extract DeviceInstanceId from the parsed XML payload
        String deviceInstanceId = safeValue(firstNonBlank(
                parsedPayload.get("DeviceInstanceId"),
                parsedPayload.get("InstanceId"),
                parsedPayload.get("DeviceId")
        )).toLowerCase(Locale.ROOT);

        // Tier 2: USBSTOR\ prefix — USBSTOR driver loaded → confirmed mass storage
        if (deviceInstanceId.startsWith("usbstor\\") || deviceInstanceId.startsWith("uaspstor\\")) {
            log.debug("USB assessment: USBSTOR device instance. eventCode={}, deviceInstanceId={}",
                    eventCode, shorten(deviceInstanceId, 120));
            return new UsbConnectionAssessment(true, true, "mass_storage", "USBSTOR device instance ID");
        }

        // Tier 3: USB\VID_ prefix — generic USB device (USBSTOR driver not loaded / blocked)
        if (deviceInstanceId.startsWith("usb\\vid_")) {
            log.debug("USB assessment: USB\\VID_ device instance. eventCode={}, deviceInstanceId={}",
                    eventCode, shorten(deviceInstanceId, 120));
            return new UsbConnectionAssessment(true, false, "usb_device", "USB VID device instance ID");
        }

        // Fallback: USBSTOR in raw payload (edge case — XML parsing may have failed)
        String rawPayload = safeValue(logEntry.getRawPayload()).toLowerCase(Locale.ROOT);
        if (rawPayload.contains("usbstor") || rawPayload.contains("uaspstor")) {
            log.debug("USB assessment: USBSTOR in raw payload. eventCode={}", eventCode);
            return new UsbConnectionAssessment(true, true, "mass_storage", "USBSTOR in event payload");
        }

        return new UsbConnectionAssessment(false, false, "unknown", "no USB storage signal");
    }

    /** Returns true for log entries that warrant USB assessment logging (for debug purposes). */
    private boolean isUsbCandidateLogEntry(DeviceLogEntry logEntry, Map<String, String> parsedPayload) {
        String eventCode = safeValue(logEntry.getEventCode());
        if ("USB_STORAGE_CONNECTED".equalsIgnoreCase(eventCode)) {
            return true;
        }
        if (!List.of("400", "410", "420", "430", "20001", "20003", "2100", "2101", "2102").contains(eventCode)) {
            return false;
        }
        String deviceInstanceId = safeValue(firstNonBlank(
                parsedPayload.get("DeviceInstanceId"),
                parsedPayload.get("InstanceId"),
                parsedPayload.get("DeviceId")
        )).toLowerCase(Locale.ROOT);
        return deviceInstanceId.contains("usbstor") || deviceInstanceId.contains("uaspstor")
                || deviceInstanceId.startsWith("usb\\vid_");
    }

    private <T> T firstNonNull(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }

    private Map<String, Object> buildUsbAssessmentContext(UsbConnectionAssessment assessment) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("accepted", assessment.accepted());
        context.put("storageLikely", assessment.storageLikely());
        context.put("deviceType", assessment.deviceType());
        context.put("deviceTypeLabel", usbDeviceTypeLabel(assessment.deviceType()));
        context.put("reason", assessment.reason());
        return context;
    }

    private String usbDeviceTypeLabel(String deviceType) {
        return switch (safeValue(deviceType)) {
            case "mass_storage" -> "USB mass storage";
            case "smartcard_reader" -> "smartcard reader";
            case "printer" -> "printer";
            case "camera" -> "kamera";
            case "hid" -> "HID zařízení";
            case "audio_device" -> "audio zařízení";
            case "bluetooth_device" -> "Bluetooth zařízení";
            case "portable_device" -> "portable device";
            case "usb_device" -> "USB zařízení";
            default -> "USB zařízení";
        };
    }

    private String describeUsbDevice(DeviceLogEntry logEntry, Map<String, String> parsedPayload) {
        String value = firstNonBlank(
                parsedPayload.get("friendlyName"),
                parsedPayload.get("model"),
                parsedPayload.get("DeviceName"),
                parsedPayload.get("FriendlyName"),
                parsedPayload.get("DeviceDescription"),
                parsedPayload.get("DriverName"),
                parsedPayload.get("pnpDeviceId"),
                parsedPayload.get("instanceId"),
                parsedPayload.get("stableId"),
                parsedPayload.get("DeviceInstanceId"),
                parsedPayload.get("InstanceId"),
                parsedPayload.get("DeviceId")
        );
        if (!"-".equals(value)) {
            return value;
        }
        return safeValue(logEntry.getMessage());
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
        Optional<DetectionFinding> mergeCandidate = findMergeCandidate(device, rule, effectiveTime);
        boolean merged = mergeCandidate.isPresent();
        DetectionFinding finding = mergeCandidate.orElseGet(DetectionFinding::new);
        LocalDateTime firstSeenAt = finding.getFirstSeenAt() == null || effectiveTime.isBefore(finding.getFirstSeenAt())
                ? effectiveTime
                : finding.getFirstSeenAt();
        LocalDateTime lastSeenAt = finding.getLastSeenAt() == null || effectiveTime.isAfter(finding.getLastSeenAt())
                ? effectiveTime
                : finding.getLastSeenAt();

        finding.setDevice(device);
        finding.setRule(rule);
        finding.setSeverity(rule.getSeverity());
        finding.setTitle(title);
        finding.setDescription(description);
        if (finding.getStatus() == null || finding.getStatus() == FindingStatusEnum.RESOLVED) {
            finding.setStatus(FindingStatusEnum.OPEN);
        }
        finding.setFirstSeenAt(firstSeenAt);
        finding.setLastSeenAt(lastSeenAt);
        finding.setContextJson(buildAggregatedFindingContext(device, finding, finding.getContextJson(), contextJson, firstSeenAt, lastSeenAt));
        finding.setCreatedByAi(false);
        DetectionFinding savedFinding = detectionFindingRepository.save(finding);
        log.info(
                "Finding {}. deviceHostname={}, ruleCode={}, findingId={}, occurredAt={}, title={}",
                merged ? "merged into existing record" : "created",
                device.getHostname(),
                rule.getCode(),
                savedFinding.getId(),
                effectiveTime,
                title
        );
        persistFindingEvent(savedFinding, contextJson);
        return savedFinding;
    }

    private Optional<DetectionFinding> findMergeCandidate(EndpointDevice device,
                                                          DetectionRule rule,
                                                          LocalDateTime occurredAt) {
        return detectionFindingRepository.findTop10ByDeviceAndRuleOrderByLastSeenAtDesc(device, rule).stream()
                .filter(candidate -> candidate.getStatus() != FindingStatusEnum.FALSE_POSITIVE)
                .filter(candidate -> candidate.getStatus() != FindingStatusEnum.RESOLVED)
                .filter(candidate -> isWithinMergeWindow(candidate, occurredAt, mergeWindowForRule(rule)))
                .findFirst();
    }

    private Duration mergeWindowForRule(DetectionRule rule) {
        if (rule == null || rule.getCode() == null) {
            return FINDING_CONTEXT_WINDOW;
        }
        if ("USB_DEVICE_CONNECTED".equalsIgnoreCase(rule.getCode())
                || "USB_BLOCKED_CONNECTION_ATTEMPT".equalsIgnoreCase(rule.getCode())) {
            return USB_FINDING_MERGE_WINDOW;
        }
        return FINDING_CONTEXT_WINDOW;
    }

    private boolean isWithinMergeWindow(DetectionFinding candidate, LocalDateTime occurredAt, Duration mergeWindow) {
        if (occurredAt == null) {
            return false;
        }
        LocalDateTime candidateFrom = candidate.getFirstSeenAt() == null ? candidate.getLastSeenAt() : candidate.getFirstSeenAt();
        LocalDateTime candidateTo = candidate.getLastSeenAt() == null ? candidate.getFirstSeenAt() : candidate.getLastSeenAt();
        if (candidateFrom == null || candidateTo == null) {
            return false;
        }
        Duration effectiveWindow = mergeWindow == null ? FINDING_CONTEXT_WINDOW : mergeWindow;
        return !occurredAt.isBefore(candidateFrom.minus(effectiveWindow))
                && !occurredAt.isAfter(candidateTo.plus(effectiveWindow));
    }

    private Map<String, Object> buildAggregatedFindingContext(EndpointDevice device,
                                                              DetectionFinding finding,
                                                              Map<String, Object> existingContext,
                                                              Map<String, Object> newContext,
                                                              LocalDateTime firstSeenAt,
                                                              LocalDateTime lastSeenAt) {
        Map<String, Object> aggregatedContext = new LinkedHashMap<>();

        Map<String, Object> deviceContext = firstNonEmptyMap(readMap(newContext, "device"), readMap(existingContext, "device"));
        if (!deviceContext.isEmpty()) {
            aggregatedContext.put("device", deviceContext);
        }

        Map<String, Object> latestTrigger = firstNonEmptyMap(readMap(newContext, "trigger"), readMap(existingContext, "trigger"));
        if (!latestTrigger.isEmpty()) {
            aggregatedContext.put("trigger", latestTrigger);
        }

        Map<String, Object> timeWindow = new LinkedHashMap<>();
        timeWindow.put("from", firstSeenAt);
        timeWindow.put("to", lastSeenAt);
        aggregatedContext.put("timeWindow", timeWindow);

        List<DeviceLogEntry> relatedLogs = deviceLogEntryRepository.findByDeviceAndOccurredAtBetweenOrderByOccurredAtAsc(device, firstSeenAt, lastSeenAt);
        List<FileSystemEvent> relatedFileEvents = fileSystemEventRepository.findByDeviceAndOccurredAtBetweenOrderByOccurredAtAsc(device, firstSeenAt, lastSeenAt);
        aggregatedContext.put("relatedLogs", relatedLogs.stream().limit(CONTEXT_ITEM_LIMIT).map(this::toLogContext).toList());
        aggregatedContext.put("relatedFileEvents", relatedFileEvents.stream().limit(CONTEXT_ITEM_LIMIT).map(this::toFileEventContext).toList());
        long triggerCount = countFindingEvents(finding);
        if (!latestTrigger.isEmpty()) {
            triggerCount += 1;
        }
        aggregatedContext.put("triggerCount", triggerCount);

        Map<String, Object> previousSnapshot = firstNonEmptyMap(readMap(newContext, "previousSnapshot"), readMap(existingContext, "previousSnapshot"));
        if (!previousSnapshot.isEmpty()) {
            aggregatedContext.put("previousSnapshot", previousSnapshot);
        }

        Map<String, Object> currentSnapshot = firstNonEmptyMap(readMap(newContext, "currentSnapshot"), readMap(existingContext, "currentSnapshot"));
        if (!currentSnapshot.isEmpty()) {
            aggregatedContext.put("currentSnapshot", currentSnapshot);
        }

        return aggregatedContext;
    }

    private Map<String, Object> readMap(Map<String, Object> source, String key) {
        if (source == null) {
            return Map.of();
        }
        Object value = source.get(key);
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }

    private Map<String, Object> firstNonEmptyMap(Map<String, Object> preferred, Map<String, Object> fallback) {
        if (preferred != null && !preferred.isEmpty()) {
            return preferred;
        }
        return fallback == null ? Map.of() : fallback;
    }

    private void persistFindingEvent(DetectionFinding finding, Map<String, Object> contextJson) {
        Map<String, Object> trigger = readMap(contextJson, "trigger");
        if (trigger.isEmpty()) {
            return;
        }

        UUID sourceRecordId = parseUuid(trigger.get("sourceRecordId"));
        if (sourceRecordId != null && detectionFindingEventRepository.existsByFindingAndSourceRecordId(finding, sourceRecordId)) {
            log.info(
                    "Finding trigger event skipped as duplicate. findingId={}, sourceRecordId={}, eventCode={}, message={}",
                    finding.getId(),
                    sourceRecordId,
                    asString(trigger.get("eventCode")),
                    shorten(asString(trigger.get("message")), 180)
            );
            return;
        }

        DetectionFindingEvent event = new DetectionFindingEvent();
        event.setFinding(finding);
        event.setEventType(parseEventType(trigger.get("type")));
        event.setOccurredAt(parseLocalDateTime(trigger.get("occurredAt")));
        event.setSourceRecordId(sourceRecordId);
        event.setSourceLog(asString(trigger.get("logSource")));
        event.setLevel(asString(trigger.get("level")));
        event.setEventCode(asString(trigger.get("eventCode")));
        event.setMessage(asString(trigger.get("message")));
        event.setPath(asString(trigger.get("path")));
        event.setActorUsername(asString(trigger.get("actorUsername")));
        event.setPayloadJson(new LinkedHashMap<>(trigger));
        detectionFindingEventRepository.save(event);
        log.info(
                "Finding trigger event persisted. findingId={}, sourceRecordId={}, eventCode={}, message={}",
                finding.getId(),
                sourceRecordId,
                event.getEventCode(),
                shorten(event.getMessage(), 180)
        );
    }

    private DetectionFindingEventTypeEnum parseEventType(Object value) {
        try {
            return DetectionFindingEventTypeEnum.valueOf(String.valueOf(value).toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            return DetectionFindingEventTypeEnum.LOG;
        }
    }

    private UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (Exception exception) {
            return null;
        }
    }

    private LocalDateTime parseLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (Exception exception) {
            return null;
        }
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value);
        return normalized.isBlank() ? null : normalized;
    }

    private long countFindingEvents(DetectionFinding finding) {
        if (finding == null || finding.getId() == null) {
            return 0;
        }
        return detectionFindingEventRepository.findByFindingOrderByOccurredAtAscIdAsc(finding).size();
    }

    private Map<String, Object> buildLogContext(EndpointDevice device,
                                                DeviceLogEntry triggerEntry,
                                                Map<String, String> parsedPayload) {
        return buildLogContext(device, triggerEntry, parsedPayload, null);
    }

    private Map<String, Object> buildLogContext(EndpointDevice device,
                                                DeviceLogEntry triggerEntry,
                                                Map<String, String> parsedPayload,
                                                UsbConnectionAssessment usbAssessment) {
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
        trigger.put("sourceRecordId", triggerEntry.getId());
        trigger.put("logSource", triggerEntry.getLogSource() == null ? null : triggerEntry.getLogSource().name());
        trigger.put("eventCode", triggerEntry.getEventCode());
        trigger.put("level", triggerEntry.getLevel());
        trigger.put("message", triggerEntry.getMessage());
        trigger.put("user", deriveLogUserLabel(parsedPayload));
        trigger.put("processName", firstNonBlank(
                parsedPayload.get("NewProcessName"),
                parsedPayload.get("ProcessName"),
                parsedPayload.get("HostApplication"),
                parsedPayload.get("CommandInvocation")
        ));
        trigger.put("parentProcessName", firstNonBlank(
                parsedPayload.get("CreatorProcessName"),
                parsedPayload.get("ParentProcessName")
        ));
        trigger.put("commandLine", parsedPayload.get("CommandLine"));
        trigger.put("hostApplication", parsedPayload.get("HostApplication"));
        trigger.put("tokenElevationType", parsedPayload.get("TokenElevationType"));
        trigger.put("activityId", parsedPayload.get("ActivityId"));
        trigger.put("parsedPayload", parsedPayload);
        if (usbAssessment != null) {
            trigger.put("usbAssessment", buildUsbAssessmentContext(usbAssessment));
        }
        context.put("trigger", trigger);
        Map<String, Object> timeWindow = new LinkedHashMap<>();
        timeWindow.put("from", from);
        timeWindow.put("to", to);
        context.put("timeWindow", timeWindow);
        context.put("relatedLogs", relatedLogs.stream().limit(CONTEXT_ITEM_LIMIT).map(this::toLogContext).toList());
        context.put("relatedFileEvents", relatedFileEvents.stream().limit(CONTEXT_ITEM_LIMIT).map(this::toFileEventContext).toList());
        return context;
    }

    private String buildPowerShellFindingDescription(DeviceLogEntry logEntry, Map<String, String> parsedPayload) {
        String eventCode = safeValue(logEntry.getEventCode());
        String user = deriveLogUserLabel(parsedPayload);
        if ("POWERSHELL_ELEVATED_LIVE".equalsIgnoreCase(eventCode)) {
            user = firstNonBlank(parsedPayload.get("user"), parsedPayload.get("subjectUser"), user);
        }
        String processName = firstNonBlank(parsedPayload.get("NewProcessName"), parsedPayload.get("ProcessName"), parsedPayload.get("HostApplication"));
        if ("POWERSHELL_ELEVATED_LIVE".equalsIgnoreCase(eventCode)) {
            processName = firstNonBlank(parsedPayload.get("processName"), processName);
        }
        String parentProcess = firstNonBlank(parsedPayload.get("CreatorProcessName"), parsedPayload.get("ParentProcessName"));
        if ("POWERSHELL_ELEVATED_LIVE".equalsIgnoreCase(eventCode)) {
            parentProcess = firstNonBlank(parsedPayload.get("parentProcessName"), parentProcess);
        }
        String commandLine = firstNonBlank(parsedPayload.get("CommandLine"), parsedPayload.get("CommandInvocation"), parsedPayload.get("Payload"));
        if ("POWERSHELL_ELEVATED_LIVE".equalsIgnoreCase(eventCode)) {
            commandLine = firstNonBlank(parsedPayload.get("commandLine"), commandLine);
        }
        String tokenElevation = safeValue(parsedPayload.get("TokenElevationType"));
        if ("POWERSHELL_ELEVATED_LIVE".equalsIgnoreCase(eventCode)) {
            tokenElevation = firstNonBlank(parsedPayload.get("tokenElevationType"), tokenElevation);
        }
        String pid = firstNonBlank(parsedPayload.get("ProcessId"), parsedPayload.get("PID"), parsedPayload.get("ThreadId"));
        if ("POWERSHELL_ELEVATED_LIVE".equalsIgnoreCase(eventCode)) {
            pid = firstNonBlank(parsedPayload.get("processId"), pid);
        }
        String activityId = safeValue(parsedPayload.get("ActivityId"));

        return switch (eventCode) {
            case "POWERSHELL_ELEVATED_LIVE" -> "PowerShell spuštěn s elevovanými právy uživatelem " + user +
                    detailSuffix("proces " + shorten(processName, 180)) +
                    detailSuffix("pid " + pid) +
                    detailSuffix("parent " + shorten(parentProcess, 140)) +
                    detailSuffix("elevation " + tokenElevation) +
                    detailSuffix("cmd " + shorten(commandLine, 220));
            case "4688" -> "PowerShell spuštěn uživatelem " + user +
                    detailSuffix("proces " + shorten(processName, 180)) +
                    detailSuffix("parent " + shorten(parentProcess, 140)) +
                    detailSuffix("elevation " + tokenElevation) +
                    detailSuffix("cmd " + shorten(commandLine, 220));
            case "4104" -> "PowerShell script block uživatele " + user +
                    detailSuffix("cmd " + shorten(commandLine, 220)) +
                    detailSuffix("activity " + activityId);
            case "4103" -> "PowerShell command invocation uživatele " + user +
                    detailSuffix("cmd " + shorten(commandLine, 220)) +
                    detailSuffix("activity " + activityId);
            case "400", "40961" -> "PowerShell engine start v kontextu " + user +
                    detailSuffix("pid " + pid) +
                    detailSuffix("elevation " + tokenElevation) +
                    detailSuffix("host " + shorten(firstNonBlank(parsedPayload.get("HostApplication"), parsedPayload.get("EngineVersion")), 180));
            default -> logEntry.getMessage() +
                    detailSuffix("uživatel " + user) +
                    detailSuffix("proces " + shorten(processName, 180));
        };
    }

    private boolean isPowerShellProcessCreationLog(DeviceLogEntry logEntry, Map<String, String> parsedPayload) {
        if (logEntry == null) {
            return false;
        }
        if (!"4688".equals(safeValue(logEntry.getEventCode()))) {
            return false;
        }
        String processName = normalizePath(firstNonBlank(parsedPayload.get("NewProcessName"), parsedPayload.get("ProcessName")));
        String message = safeValue(logEntry.getMessage()).toLowerCase(Locale.ROOT);
        String rawPayload = safeValue(logEntry.getRawPayload()).toLowerCase(Locale.ROOT);
        return processName.endsWith("\\powershell.exe")
                || processName.endsWith("\\pwsh.exe")
                || message.contains("powershell.exe")
                || message.contains("pwsh.exe")
                || rawPayload.contains("\\powershell.exe")
                || rawPayload.contains("\\pwsh.exe");
    }

    private String deriveLogUserLabel(Map<String, String> parsedPayload) {
        String subjectUser = userLabel(parsedPayload, "SubjectDomainName", "SubjectUserName");
        if (!"-".equals(subjectUser)) {
            return subjectUser;
        }
        String targetUser = userLabel(parsedPayload, "TargetDomainName", "TargetUserName");
        if (!"-".equals(targetUser)) {
            return targetUser;
        }
        String userId = safeValue(parsedPayload.get("UserId"));
        if (userId.equals("-")) {
            return "-";
        }
        return friendlySecurityPrincipal(userId);
    }

    private String friendlySecurityPrincipal(String sid) {
        String normalized = safeValue(sid).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "S-1-5-18" -> "SYSTEM (" + sid + ")";
            case "S-1-5-19" -> "LocalService (" + sid + ")";
            case "S-1-5-20" -> "NetworkService (" + sid + ")";
            default -> sid;
        };
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
        trigger.put("sourceRecordId", triggerEvent.getId());
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
        trigger.put("sourceRecordId", currentSnapshot.getId());
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

    private Map<String, Object> buildOsUpdateContext(EndpointDevice device,
                                                     DeviceSnapshot previousSnapshot,
                                                     DeviceSnapshot currentSnapshot) {
        Map<String, Object> context = buildSnapshotContext(device, previousSnapshot, currentSnapshot, List.of(), List.of());
        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("type", "SNAPSHOT");
        trigger.put("occurredAt", currentSnapshot.getCollectedAt());
        trigger.put("sourceRecordId", currentSnapshot.getId());
        trigger.put("previousSnapshotVersion", previousSnapshot.getVersionNo());
        trigger.put("currentSnapshotVersion", currentSnapshot.getVersionNo());
        trigger.put("previousOperatingSystem", describeOperatingSystem(previousSnapshot));
        trigger.put("currentOperatingSystem", describeOperatingSystem(currentSnapshot));
        trigger.put("osNameChanged", !Objects.equals(safeValue(previousSnapshot.getOsName()), safeValue(currentSnapshot.getOsName())));
        trigger.put("osVersionChanged", !Objects.equals(safeValue(previousSnapshot.getOsVersion()), safeValue(currentSnapshot.getOsVersion())));
        trigger.put("osBuildChanged", !Objects.equals(safeValue(previousSnapshot.getOsBuild()), safeValue(currentSnapshot.getOsBuild())));
        context.put("trigger", trigger);
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
        summary.put("usbRemovableBlocked", device.isUsbRemovableBlocked());
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

    private record UsbConnectionAssessment(boolean accepted,
                                           boolean storageLikely,
                                           String deviceType,
                                           String reason) {
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

    private boolean hasOperatingSystemChanged(DeviceSnapshot previousSnapshot, DeviceSnapshot currentSnapshot) {
        return !Objects.equals(safeValue(previousSnapshot.getOsName()), safeValue(currentSnapshot.getOsName()))
                || !Objects.equals(safeValue(previousSnapshot.getOsVersion()), safeValue(currentSnapshot.getOsVersion()))
                || !Objects.equals(safeValue(previousSnapshot.getOsBuild()), safeValue(currentSnapshot.getOsBuild()));
    }

    private String describeOperatingSystem(DeviceSnapshot snapshot) {
        return List.of(
                        safeValue(snapshot.getOsName()),
                        safeValue(snapshot.getOsVersion()),
                        safeValue(snapshot.getOsBuild()).equals("-") ? null : "build " + safeValue(snapshot.getOsBuild())
                ).stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank() && !value.equals("-"))
                .collect(Collectors.joining(" "));
    }

    private Map<String, String> parseEventXml(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return Map.of();
        }

        String normalizedPayload = rawPayload.trim();
        if (normalizedPayload.startsWith("{")) {
            return parseJsonPayload(normalizedPayload);
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

    private Map<String, String> parseJsonPayload(String rawPayload) {
        try {
            Map<String, Object> source = OBJECT_MAPPER.readValue(rawPayload, MAP_TYPE);
            Map<String, String> flattened = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                Object value = entry.getValue();
                if (value instanceof Collection<?> collection) {
                    String joined = collection.stream()
                            .filter(Objects::nonNull)
                            .map(String::valueOf)
                            .filter(item -> !item.isBlank())
                            .collect(Collectors.joining("|"));
                    if (!joined.isBlank()) {
                        flattened.put(entry.getKey(), joined);
                    }
                    continue;
                }
                if (value instanceof Map<?, ?> nested) {
                    flattened.put(entry.getKey(), safeValue(writeJson(nested)));
                    continue;
                }
                String normalized = String.valueOf(value).trim();
                if (!normalized.isBlank()) {
                    flattened.put(entry.getKey(), normalized);
                }
            }
            return Map.copyOf(flattened);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
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

    private boolean isBenignSystemTaskPath(String normalizedPath) {
        return normalizedPath.contains("\\windows\\system32\\tasks\\microsoft\\windows\\")
                || normalizedPath.contains("\\windows\\tasks\\microsoft\\windows\\");
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

    private String shorten(String value, int maxLength) {
        String safe = safeValue(value);
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private record ElevatedPowerShellAssessment(boolean candidate,
                                                boolean accepted,
                                                String reason,
                                                String processName,
                                                String tokenElevationType,
                                                String userLabel) {
    }
}
