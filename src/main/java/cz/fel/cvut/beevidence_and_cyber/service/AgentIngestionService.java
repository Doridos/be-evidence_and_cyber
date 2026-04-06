package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.*;
import cz.fel.cvut.beevidence_and_cyber.dto.*;
import cz.fel.cvut.beevidence_and_cyber.enumeration.*;
import cz.fel.cvut.beevidence_and_cyber.exception.NotFoundException;
import cz.fel.cvut.beevidence_and_cyber.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentIngestionService {
    private static final long LOG_RETENTION_DAYS = 3;
    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Europe/Prague");

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

    @Transactional
    public DeviceDetailDto ingestHeartbeat(AgentHeartbeatRequest request) {
        EndpointDevice device = endpointDeviceRepository.findByHostnameIgnoreCase(request.device().hostname())
                .orElseGet(() -> createDeviceFromAgentPayload(request.device()));

        updateDeviceFromAgentPayload(device, request.device());
        EndpointDevice savedDevice = endpointDeviceRepository.save(device);

        DeviceSnapshot previousSnapshot = deviceSnapshotRepository.findTopByDeviceOrderByVersionNoDesc(savedDevice).orElse(null);
        List<NetworkInterface> previousInterfaces = previousSnapshot == null ? List.of() : networkInterfaceRepository.findBySnapshot(previousSnapshot);

        DeviceSnapshot snapshot = createOrReuseSnapshot(savedDevice, request.device());
        AgentHeartbeat heartbeat = createHeartbeat(savedDevice, request.device(), request.telemetry());
        createTelemetry(savedDevice, request.telemetry());

        if (previousSnapshot != null && !previousSnapshot.getId().equals(snapshot.getId())) {
            detectionService.evaluateSnapshotChanges(
                    savedDevice,
                    previousSnapshot,
                    previousInterfaces,
                    snapshot,
                    networkInterfaceRepository.findBySnapshot(snapshot)
            );
        }

        auditService.log(null, ActorSourceEnum.AGENT, "INGEST_HEARTBEAT", "DEVICE", savedDevice.getId(), AuditResultEnum.SUCCESS,
                Map.of("hostname", savedDevice.getHostname(), "snapshotId", snapshot.getId().toString(), "heartbeatId", heartbeat.getId().toString()));

        return apiMapper.toDto(
                savedDevice,
                DeviceStatusEnum.ACTIVE.name(),
                mapSnapshots(savedDevice),
                agentHeartbeatRepository.findByDeviceOrderByLastSeenAtDesc(savedDevice).stream().map(apiMapper::toDto).toList(),
                telemetrySampleRepository.findByDeviceOrderByCollectedAtDesc(savedDevice).stream().map(apiMapper::toDto).toList(),
                deviceLogEntryRepository.findByDeviceOrderByOccurredAtDesc(savedDevice).stream().map(apiMapper::toDto).toList(),
                fileSystemEventRepository.findByDeviceOrderByOccurredAtDesc(savedDevice).stream().map(apiMapper::toDto).toList()
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

        List<DeviceLogEntry> savedLogEntries = new ArrayList<>();
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
                savedLogEntries.add(deviceLogEntryRepository.save(logEntry));
            }
        }

        List<FileSystemEvent> savedFileEvents = new ArrayList<>();
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
                savedFileEvents.add(fileSystemEventRepository.save(event));
            }
        }

        detectionService.evaluateCollectedSignals(device, savedLogEntries, savedFileEvents);
        pruneOldCollectedData(device);

        auditService.log(null, ActorSourceEnum.AGENT, "INGEST_LOGS", "DEVICE", device.getId(), AuditResultEnum.SUCCESS,
                Map.of("hostname", device.getHostname()));
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
        return endpointDeviceRepository.save(device);
    }

    private void updateDeviceFromAgentPayload(EndpointDevice device, AgentDevicePayload payload) {
        device.setFqdn(payload.fqdn());
        device.setPrimaryIp(payload.primaryIp());
        device.setAgentInstalled(true);
        if (device.getDiscoveredAt() == null) {
            device.setDiscoveredAt(now(payload.collectedAt()));
        }
    }

    private DeviceSnapshot createOrReuseSnapshot(EndpointDevice device, AgentDevicePayload payload) {
        LocalDateTime collectedAt = now(payload.collectedAt());
        DeviceSnapshot latestSnapshot = deviceSnapshotRepository.findTopByDeviceOrderByVersionNoDesc(device).orElse(null);
        if (latestSnapshot != null) {
            List<NetworkInterface> latestInterfaces = networkInterfaceRepository.findBySnapshot(latestSnapshot);
            List<LoggedInSession> latestSessions = loggedInSessionRepository.findBySnapshot(latestSnapshot);
            if (snapshotMatches(latestSnapshot, latestInterfaces, latestSessions, payload)) {
                return latestSnapshot;
            }

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
                networkInterfaceRepository.save(networkInterface);
            }
        }

        if (payload.loggedInSessions() != null) {
            for (AgentLoggedInSessionPayload sessionPayload : payload.loggedInSessions()) {
                LoggedInSession session = new LoggedInSession();
                session.setSnapshot(savedSnapshot);
                session.setUsername(sessionPayload.username());
                session.setDomain(sessionPayload.domain());
                session.setSessionType(parseSessionType(sessionPayload.sessionType()));
                session.setState(parseSessionState(sessionPayload.state()));
                session.setLoginTime(now(sessionPayload.loginTime()));
                loggedInSessionRepository.save(session);
            }
        }

        return savedSnapshot;
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

    private void pruneOldCollectedData(EndpointDevice device) {
        LocalDateTime retentionCutoff = LocalDateTime.now(APPLICATION_ZONE).minusDays(LOG_RETENTION_DAYS);
        deviceLogEntryRepository.deleteByDeviceAndOccurredAtBefore(device, retentionCutoff);
        fileSystemEventRepository.deleteByDeviceAndOccurredAtBefore(device, retentionCutoff);
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
}
