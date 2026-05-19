package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.config.CacheConfig;
import cz.fel.cvut.beevidence_and_cyber.dao.*;
import cz.fel.cvut.beevidence_and_cyber.dto.*;
import cz.fel.cvut.beevidence_and_cyber.enumeration.*;
import cz.fel.cvut.beevidence_and_cyber.exception.NotFoundException;
import cz.fel.cvut.beevidence_and_cyber.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentIngestionService {
    private static final long LOG_RETENTION_DAYS = 3;
    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Europe/Prague");
    private static final Duration INLINE_LOG_PRUNE_INTERVAL = Duration.ofHours(6);

    private final EndpointDeviceRepository endpointDeviceRepository;
    private final DeviceSnapshotRepository deviceSnapshotRepository;
    private final NetworkInterfaceRepository networkInterfaceRepository;
    private final LoggedInSessionRepository loggedInSessionRepository;
    private final AgentHeartbeatRepository agentHeartbeatRepository;
    private final TelemetrySampleRepository telemetrySampleRepository;
    private final RemoteHelpRequestRepository remoteHelpRequestRepository;
    private final DeviceLogEntryRepository deviceLogEntryRepository;
    private final FileSystemEventRepository fileSystemEventRepository;
    private final CommandRequestRepository commandRequestRepository;
    private final CommandExecutionRepository commandExecutionRepository;
    private final AuditService auditService;
    private final ApiMapper apiMapper;
    private final DetectionService detectionService;
    private final CacheManager cacheManager;
    private final Map<UUID, LocalDateTime> lastInlinePruneAtByDevice = new ConcurrentHashMap<>();

    @Transactional
    public AgentIngestionAckDto ingestHeartbeat(AgentHeartbeatRequest request) {
        Optional<EndpointDevice> existingDevice = endpointDeviceRepository.findByHostnameIgnoreCase(request.device().hostname());
        EndpointDevice device = existingDevice.orElseGet(() -> createDeviceFromAgentPayload(request.device()));
        boolean shouldEvictDeviceListCache = existingDevice.isEmpty();
        shouldEvictDeviceListCache |= updateDeviceFromAgentPayload(device, request.device());
        EndpointDevice savedDevice = endpointDeviceRepository.save(device);

        DeviceSnapshot previousSnapshot = deviceSnapshotRepository.findTopByDeviceOrderByVersionNoDesc(savedDevice).orElse(null);
        List<NetworkInterface> previousInterfaces = previousSnapshot == null ? List.of() : networkInterfaceRepository.findBySnapshot(previousSnapshot);
        List<LoggedInSession> previousSessions = previousSnapshot == null ? List.of() : loggedInSessionRepository.findBySnapshot(previousSnapshot);

        SnapshotIngestionResult snapshotResult = createOrReuseSnapshot(savedDevice, request.device(), previousSnapshot, previousInterfaces, previousSessions);
        AgentHeartbeat heartbeat = createHeartbeat(savedDevice, request.device(), request.telemetry());
        TelemetrySample telemetry = createTelemetry(savedDevice, request.telemetry());

        if (previousSnapshot != null && snapshotResult.createdNewSnapshot()) {
            detectionService.evaluateSnapshotChanges(
                    savedDevice,
                    previousSnapshot,
                    previousInterfaces,
                    snapshotResult.snapshot(),
                    snapshotResult.networkInterfaces()
            );
        }

        if (shouldEvictDeviceListCache) {
            evictDeviceListCache();
        }

        auditService.log(null, ActorSourceEnum.AGENT, "INGEST_HEARTBEAT", "DEVICE", savedDevice.getId(), AuditResultEnum.SUCCESS,
                Map.of(
                        "hostname", savedDevice.getHostname(),
                        "snapshotId", snapshotResult.snapshot().getId().toString(),
                        "heartbeatId", heartbeat.getId().toString()
                ));

        return new AgentIngestionAckDto(
                savedDevice.getId(),
                snapshotResult.snapshot().getId(),
                heartbeat.getId(),
                telemetry.getId(),
                snapshotResult.createdNewSnapshot()
        );
    }

    @Transactional
    public RemoteHelpRequestDto ingestHelpRequest(AgentHelpRequestInput request) {
        EndpointDevice device = endpointDeviceRepository.findByHostnameIgnoreCase(request.deviceHostname())
                .orElseGet(() -> {
                    EndpointDevice newDevice = new EndpointDevice();
                    newDevice.setHostname(request.deviceHostname());
                    newDevice.setFqdn(request.deviceFqdn());
                    newDevice.setPrimaryIp(request.primaryIp());
                    newDevice.setStatus(DeviceStatusEnum.ACTIVE);
                    newDevice.setAgentInstalled(true);
                    newDevice.setDiscoveredAt(now(request.requestedAt()));
                    return endpointDeviceRepository.save(newDevice);
                });

        RemoteHelpRequest helpRequest = new RemoteHelpRequest();
        helpRequest.setDevice(device);
        helpRequest.setRequestedByUsername(request.requestedByUsername());
        helpRequest.setRequestedByDisplayName(request.requestedByDisplayName());
        helpRequest.setMessage(request.message());
        helpRequest.setStatus(HelpRequestStatusEnum.NEW);
        helpRequest.setRequestedAt(now(request.requestedAt()));

        RemoteHelpRequest saved = remoteHelpRequestRepository.save(helpRequest);
        auditService.log(null, ActorSourceEnum.AGENT, "INGEST_HELP_REQUEST", "REMOTE_HELP_REQUEST", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("requestedBy", saved.getRequestedByUsername()));
        return apiMapper.toDto(saved);
    }

    @Transactional
    public void ingestLogs(AgentLogIngestionRequest request) {
        EndpointDevice device = endpointDeviceRepository.findByHostnameIgnoreCase(request.deviceHostname())
                .orElseThrow(() -> new NotFoundException("Device with hostname " + request.deviceHostname() + " not found"));
        log.info(
                "Agent log ingestion started. deviceHostname={}, incomingLogEntries={}, incomingFileEvents={}",
                device.getHostname(),
                request.logEntries() == null ? 0 : request.logEntries().size(),
                request.fileSystemEvents() == null ? 0 : request.fileSystemEvents().size()
        );

        List<DeviceLogEntry> logEntriesToPersist = new ArrayList<>(request.logEntries() == null ? 0 : request.logEntries().size());
        long usbRelatedLogEntries = 0;
        long elevatedPowerShellLogEntries = 0;
        if (request.logEntries() != null) {
            for (AgentLogEntryPayload payload : request.logEntries()) {
                DeviceLogEntry logEntry = new DeviceLogEntry();
                logEntry.setDevice(device);
                logEntry.setOccurredAt(now(payload.occurredAt()));
                logEntry.setLogSource(payload.logSource() == null ? LogSourceEnum.AGENT : LogSourceEnum.valueOf(payload.logSource().toUpperCase()));
                logEntry.setLevel(payload.level());
                logEntry.setEventCode(payload.eventCode());
                logEntry.setMessage(payload.message());
                logEntry.setRawPayload(payload.rawPayload());
                if (isUsbRelatedLogEntry(logEntry)) {
                    usbRelatedLogEntries++;
                }
                if (isElevatedPowerShellRelatedLogEntry(logEntry)) {
                    elevatedPowerShellLogEntries++;
                }
                logEntriesToPersist.add(logEntry);
            }
        }
        List<DeviceLogEntry> savedLogEntries = logEntriesToPersist.isEmpty()
                ? List.of()
                : deviceLogEntryRepository.saveAll(logEntriesToPersist);

        List<FileSystemEvent> fileEventsToPersist = new ArrayList<>(request.fileSystemEvents() == null ? 0 : request.fileSystemEvents().size());
        if (request.fileSystemEvents() != null) {
            for (AgentFileSystemEventPayload payload : request.fileSystemEvents()) {
                FileSystemEvent event = new FileSystemEvent();
                event.setDevice(device);
                event.setOccurredAt(now(payload.occurredAt()));
                event.setEventType(payload.eventType() == null ? null : FileEventTypeEnum.valueOf(payload.eventType().toUpperCase()));
                event.setPath(payload.path());
                event.setActorUsername(payload.actorUsername());
                event.setSourceLog(payload.sourceLog());
                event.setDetailsJson(payload.detailsJson());
                fileEventsToPersist.add(event);
            }
        }
        List<FileSystemEvent> savedFileEvents = fileEventsToPersist.isEmpty()
                ? List.of()
                : fileSystemEventRepository.saveAll(fileEventsToPersist);

        log.info(
                "Agent log ingestion persisted. deviceHostname={}, savedLogEntries={}, savedFileEvents={}, usbRelatedLogEntries={}, elevatedPowerShellLogEntries={}",
                device.getHostname(),
                savedLogEntries.size(),
                savedFileEvents.size(),
                usbRelatedLogEntries,
                elevatedPowerShellLogEntries
        );
        detectionService.evaluateCollectedSignals(device, savedLogEntries, savedFileEvents);
        maybePruneOldCollectedData(device);

        auditService.log(null, ActorSourceEnum.AGENT, "INGEST_LOGS", "DEVICE", device.getId(), AuditResultEnum.SUCCESS,
                Map.of("hostname", device.getHostname()));
    }

    private boolean isUsbRelatedLogEntry(DeviceLogEntry entry) {
        if (entry == null) {
            return false;
        }
        String eventCode = entry.getEventCode() == null ? "" : entry.getEventCode().toUpperCase(Locale.ROOT);
        String message = entry.getMessage() == null ? "" : entry.getMessage().toLowerCase(Locale.ROOT);
        String rawPayload = entry.getRawPayload() == null ? "" : entry.getRawPayload().toLowerCase(Locale.ROOT);
        return eventCode.contains("USB")
                || List.of("400", "410", "420", "430", "20001", "20003", "2100", "2101", "2102").contains(eventCode)
                || message.contains("usb")
                || rawPayload.contains("usb")
                || rawPayload.contains("usbstor")
                || rawPayload.contains("uaspstor");
    }

    private boolean isElevatedPowerShellRelatedLogEntry(DeviceLogEntry entry) {
        if (entry == null) {
            return false;
        }
        String eventCode = entry.getEventCode() == null ? "" : entry.getEventCode().toUpperCase(Locale.ROOT);
        String message = entry.getMessage() == null ? "" : entry.getMessage().toLowerCase(Locale.ROOT);
        return "POWERSHELL_ELEVATED_LIVE".equals(eventCode)
                || eventCode.equals("4688")
                || message.contains("elevated powershell")
                || message.contains("spuštění powershellu s elevovanými právy");
    }

    @Transactional
    public CommandExecutionDto ingestCommandExecution(AgentCommandExecutionRequest request) {
        CommandRequest commandRequest = commandRequestRepository.findById(request.commandRequestId())
                .orElseThrow(() -> new NotFoundException("Command request with id " + request.commandRequestId() + " not found"));

        CommandExecution execution = new CommandExecution();
        execution.setCommandRequest(commandRequest);
        if (request.agentHeartbeatId() != null) {
            AgentHeartbeat heartbeat = agentHeartbeatRepository.findById(request.agentHeartbeatId())
                    .orElseThrow(() -> new NotFoundException("Agent heartbeat with id " + request.agentHeartbeatId() + " not found"));
            execution.setAgentHeartbeat(heartbeat);
        }
        execution.setStartedAt(now(request.startedAt()));
        execution.setFinishedAt(now(request.finishedAt()));
        execution.setExitCode(request.exitCode());
        execution.setResultSummary(request.resultSummary());
        execution.setErrorMessage(request.errorMessage());
        execution.setResultJson(request.resultJson());

        if (execution.getFinishedAt() != null) {
            commandRequest.setStatus(request.exitCode() != null && request.exitCode() == 0 ? CommandStatusEnum.SUCCESS : CommandStatusEnum.FAILED);
        } else {
            commandRequest.setStatus(CommandStatusEnum.RUNNING);
        }

        if (request.exitCode() != null
                && request.exitCode() == 0
                && commandRequest.getCommandType() == CommandTypeEnum.USB
                && commandRequest.getDevice() != null) {
            String mode = null;
            if (request.resultJson() != null && request.resultJson().get("mode") instanceof String resultMode) {
                mode = resultMode;
            } else if (commandRequest.getPayloadJson() != null && commandRequest.getPayloadJson().get("mode") instanceof String payloadMode) {
                mode = payloadMode;
            }
            if (mode != null) {
                commandRequest.getDevice().setUsbRemovableBlocked("BLOCK".equalsIgnoreCase(mode));
                endpointDeviceRepository.save(commandRequest.getDevice());
            }
        }

        commandRequestRepository.save(commandRequest);

        CommandExecution saved = commandExecutionRepository.save(execution);
        auditService.log(null, ActorSourceEnum.AGENT, "INGEST_COMMAND_EXECUTION", "COMMAND_EXECUTION", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("commandRequestId", commandRequest.getId().toString()));
        return apiMapper.toDto(saved);
    }

    private EndpointDevice createDeviceFromAgentPayload(AgentDevicePayload payload) {
        EndpointDevice device = new EndpointDevice();
        device.setHostname(payload.hostname());
        device.setFqdn(payload.fqdn());
        device.setPrimaryIp(payload.primaryIp());
        device.setStatus(DeviceStatusEnum.ACTIVE);
        device.setAgentInstalled(true);
        device.setDiscoveredAt(now(payload.collectedAt()));
        return device;
    }

    private boolean updateDeviceFromAgentPayload(EndpointDevice device, AgentDevicePayload payload) {
        boolean changed = false;
        if (!equalsNullable(device.getFqdn(), payload.fqdn())) {
            device.setFqdn(payload.fqdn());
            changed = true;
        }
        if (!equalsNullable(device.getPrimaryIp(), payload.primaryIp())) {
            device.setPrimaryIp(payload.primaryIp());
            changed = true;
        }
        if (!device.isAgentInstalled()) {
            device.setAgentInstalled(true);
            changed = true;
        }
        if (device.getDiscoveredAt() == null) {
            device.setDiscoveredAt(now(payload.collectedAt()));
            changed = true;
        }
        return changed;
    }

    private SnapshotIngestionResult createOrReuseSnapshot(EndpointDevice device,
                                                          AgentDevicePayload payload,
                                                          DeviceSnapshot latestSnapshot,
                                                          List<NetworkInterface> latestInterfaces,
                                                          List<LoggedInSession> latestSessions) {
        LocalDateTime collectedAt = now(payload.collectedAt());
        if (latestSnapshot != null && snapshotMatches(latestSnapshot, latestInterfaces, latestSessions, payload)) {
            return new SnapshotIngestionResult(latestSnapshot, latestInterfaces, false);
        }
        if (latestSnapshot != null) {
            latestSnapshot.setValidTo(collectedAt);
            deviceSnapshotRepository.save(latestSnapshot);
        }

        int lastVersion = latestSnapshot == null ? 0 : latestSnapshot.getVersionNo();

        DeviceSnapshot snapshot = new DeviceSnapshot();
        snapshot.setDevice(device);
        snapshot.setVersionNo(lastVersion + 1);
        snapshot.setCollectedAt(collectedAt);
        snapshot.setValidFrom(collectedAt);
        snapshot.setHostname(payload.hostname());
        snapshot.setOsName(payload.osName());
        snapshot.setOsVersion(payload.osVersion());
        snapshot.setOsBuild(payload.osBuild());
        snapshot.setOsArchitecture(payload.osArchitecture());
        snapshot.setDomainName(payload.domainName());
        snapshot.setCurrentLoggedUser(payload.currentLoggedUser());
        snapshot.setLastBootAt(now(payload.lastBootAt()));
        snapshot.setJavaAgentVersion(payload.agentVersion());
        DeviceSnapshot savedSnapshot = deviceSnapshotRepository.save(snapshot);

        List<NetworkInterface> savedInterfaces = new ArrayList<>(payload.networkAdapters() == null ? 0 : payload.networkAdapters().size());
        if (payload.networkAdapters() != null) {
            for (AgentNetworkAdapterPayload adapterPayload : payload.networkAdapters()) {
                NetworkInterface networkInterface = new NetworkInterface();
                networkInterface.setSnapshot(savedSnapshot);
                networkInterface.setName(adapterPayload.name());
                networkInterface.setDisplayName(adapterPayload.displayName());
                networkInterface.setMacAddress(adapterPayload.macAddress());
                networkInterface.setIpv4(firstItem(adapterPayload.ipv4Addresses()));
                networkInterface.setIpv6(firstItem(adapterPayload.ipv6Addresses()));
                networkInterface.setPrimary(adapterPayload.primary());
                networkInterface.setUp(adapterPayload.up());
                savedInterfaces.add(networkInterface);
            }
        }
        if (!savedInterfaces.isEmpty()) {
            savedInterfaces = networkInterfaceRepository.saveAll(savedInterfaces);
        }

        List<LoggedInSession> savedSessions = new ArrayList<>(payload.loggedInSessions() == null ? 0 : payload.loggedInSessions().size());
        if (payload.loggedInSessions() != null) {
            for (AgentLoggedInSessionPayload sessionPayload : payload.loggedInSessions()) {
                LoggedInSession session = new LoggedInSession();
                session.setSnapshot(savedSnapshot);
                session.setUsername(sessionPayload.username());
                session.setDomain(sessionPayload.domain());
                session.setSessionType(parseSessionType(sessionPayload.sessionType()));
                session.setState(parseSessionState(sessionPayload.state()));
                session.setLoginTime(now(sessionPayload.loginTime()));
                savedSessions.add(session);
            }
        }
        if (!savedSessions.isEmpty()) {
            loggedInSessionRepository.saveAll(savedSessions);
        }

        return new SnapshotIngestionResult(savedSnapshot, List.copyOf(savedInterfaces), true);
    }

    private boolean snapshotMatches(DeviceSnapshot snapshot,
                                    List<NetworkInterface> networkInterfaces,
                                    List<LoggedInSession> loggedInSessions,
                                    AgentDevicePayload payload) {
        if (!equalsNullable(snapshot.getHostname(), payload.hostname())) {
            return false;
        }
        if (!equalsNullable(snapshot.getOsName(), payload.osName())) {
            return false;
        }
        if (!equalsNullable(snapshot.getOsVersion(), payload.osVersion())) {
            return false;
        }
        if (!equalsNullable(snapshot.getOsBuild(), payload.osBuild())) {
            return false;
        }
        if (!equalsNullable(snapshot.getOsArchitecture(), payload.osArchitecture())) {
            return false;
        }
        if (!equalsNullable(snapshot.getDomainName(), payload.domainName())) {
            return false;
        }
        if (!equalsNullable(snapshot.getCurrentLoggedUser(), payload.currentLoggedUser())) {
            return false;
        }
        if (!equalsNullable(snapshot.getJavaAgentVersion(), payload.agentVersion())) {
            return false;
        }
        if (!equalsNullable(snapshot.getLastBootAt(), now(payload.lastBootAt()))) {
            return false;
        }

        List<String> existingNetworkSignatures = networkInterfaces.stream()
                .map(networkInterface -> String.join("|",
                        safeValue(networkInterface.getName()),
                        safeValue(networkInterface.getDisplayName()),
                        safeValue(networkInterface.getMacAddress()),
                        safeValue(networkInterface.getIpv4()),
                        String.valueOf(networkInterface.isPrimary())))
                .sorted()
                .toList();
        List<String> payloadNetworkSignatures = (payload.networkAdapters() == null ? List.<AgentNetworkAdapterPayload>of() : payload.networkAdapters()).stream()
                .map(adapterPayload -> String.join("|",
                        safeValue(adapterPayload.name()),
                        safeValue(adapterPayload.displayName()),
                        safeValue(adapterPayload.macAddress()),
                        safeValue(firstItem(adapterPayload.ipv4Addresses())),
                        String.valueOf(adapterPayload.primary())))
                .sorted()
                .toList();
        if (!existingNetworkSignatures.equals(payloadNetworkSignatures)) {
            return false;
        }

        List<String> existingSessionSignatures = loggedInSessions.stream()
                .map(session -> String.join("|",
                        safeValue(session.getUsername()),
                        safeValue(session.getSessionType() == null ? null : session.getSessionType().name())))
                .sorted()
                .toList();
        List<String> payloadSessionSignatures = (payload.loggedInSessions() == null ? List.<AgentLoggedInSessionPayload>of() : payload.loggedInSessions()).stream()
                .map(sessionPayload -> String.join("|",
                        safeValue(sessionPayload.username()),
                        safeValue(normalizeEnumValue(sessionPayload.sessionType()))))
                .sorted()
                .toList();
        return existingSessionSignatures.equals(payloadSessionSignatures);
    }

    private AgentHeartbeat createHeartbeat(EndpointDevice device, AgentDevicePayload payload, AgentTelemetryPayload telemetryPayload) {
        AgentHeartbeat heartbeat = new AgentHeartbeat();
        heartbeat.setDevice(device);
        heartbeat.setAgentVersion(payload.agentVersion());
        heartbeat.setServiceStatus(ServiceStatusEnum.ONLINE);
        heartbeat.setLastSeenAt(now(payload.collectedAt()));
        heartbeat.setLastCollectAt(now(telemetryPayload.collectedAt()));
        return agentHeartbeatRepository.save(heartbeat);
    }

    private TelemetrySample createTelemetry(EndpointDevice device, AgentTelemetryPayload payload) {
        TelemetrySample telemetrySample = new TelemetrySample();
        telemetrySample.setDevice(device);
        telemetrySample.setCollectedAt(now(payload.collectedAt()));
        telemetrySample.setCpuUsagePct(defaultDecimal(payload.cpuUsagePct()));
        telemetrySample.setMemoryUsagePct(defaultDecimal(payload.memoryUsagePct()));
        telemetrySample.setDiskUsagePct(defaultDecimal(payload.diskUsagePct()));
        telemetrySample.setProcessCount(payload.processCount());
        telemetrySample.setServiceCount(payload.serviceCount());
        return telemetrySampleRepository.save(telemetrySample);
    }

    private LocalDateTime now(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null
                ? LocalDateTime.now(APPLICATION_ZONE)
                : offsetDateTime.atZoneSameInstant(APPLICATION_ZONE).toLocalDateTime();
    }

    private void maybePruneOldCollectedData(EndpointDevice device) {
        if (device == null || device.getId() == null) {
            return;
        }

        LocalDateTime pruneStartedAt = LocalDateTime.now(APPLICATION_ZONE);
        LocalDateTime lastPrunedAt = lastInlinePruneAtByDevice.get(device.getId());
        if (lastPrunedAt != null && lastPrunedAt.plus(INLINE_LOG_PRUNE_INTERVAL).isAfter(pruneStartedAt)) {
            return;
        }

        lastInlinePruneAtByDevice.put(device.getId(), pruneStartedAt);
        LocalDateTime retentionCutoff = LocalDateTime.now(APPLICATION_ZONE).minusDays(LOG_RETENTION_DAYS);
        deviceLogEntryRepository.deleteByDeviceAndOccurredAtBefore(device, retentionCutoff);
        fileSystemEventRepository.deleteByDeviceAndOccurredAtBefore(device, retentionCutoff);
    }

    private void evictDeviceListCache() {
        Cache cache = cacheManager.getCache(CacheConfig.DEVICE_LIST_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    private String firstItem(List<String> items) {
        return items == null || items.isEmpty() ? null : items.getFirst();
    }

    private boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private SessionTypeEnum parseSessionType(String rawValue) {
        String normalizedValue = normalizeEnumValue(rawValue);
        if (normalizedValue == null) {
            return null;
        }

        try {
            return SessionTypeEnum.valueOf(normalizedValue);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private SessionStateEnum parseSessionState(String rawValue) {
        String normalizedValue = normalizeEnumValue(rawValue);
        if (normalizedValue == null) {
            return null;
        }

        try {
            return SessionStateEnum.valueOf(normalizedValue);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalizeEnumValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String normalizedValue = rawValue.trim();
        if (normalizedValue.isEmpty()) {
            return null;
        }

        return normalizedValue
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private List<DeviceSnapshotDto> mapSnapshots(EndpointDevice device) {
        return deviceSnapshotRepository.findByDeviceOrderByCollectedAtDesc(device).stream()
                .map(snapshot -> apiMapper.toDto(
                        snapshot,
                        networkInterfaceRepository.findBySnapshot(snapshot),
                        loggedInSessionRepository.findBySnapshot(snapshot)
                ))
                .toList();
    }

    private record SnapshotIngestionResult(
            DeviceSnapshot snapshot,
            List<NetworkInterface> networkInterfaces,
            boolean createdNewSnapshot
    ) {
    }
}
