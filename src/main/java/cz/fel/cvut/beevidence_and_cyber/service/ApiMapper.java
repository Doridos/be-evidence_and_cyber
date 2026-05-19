package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.*;
import cz.fel.cvut.beevidence_and_cyber.dto.*;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ApiMapper {

    public UserDto toDto(User user, List<RoleDto> roles) {
        return new UserDto(
                user.getId(),
                user.getAdUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getDepartment(),
                user.isEnabled(),
                user.getLastLoginAt(),
                user.getSource(),
                roles
        );
    }

    public RoleDto toDto(Role role) {
        return new RoleDto(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isSystem());
    }

    public DeviceDetailDto toDto(EndpointDevice device,
                                 String effectiveStatus,
                                 String currentLoggedUser,
                                 List<DeviceSnapshotDto> snapshots,
                                 List<AgentHeartbeatDto> heartbeats,
                                 List<TelemetrySampleDto> telemetrySamples,
                                 List<DeviceLogEntryDto> logEntries,
                                 List<FileSystemEventDto> fileSystemEvents) {
        DeviceOwner owner = device.getOwner();
        DeviceDepartment department = device.getDepartment();
        String ownerFirstName = owner == null ? device.getOwnerFirstName() : owner.getFirstName();
        String ownerLastName = owner == null ? device.getOwnerLastName() : owner.getLastName();
        return new DeviceDetailDto(
                device.getId(),
                device.getAssetTag(),
                device.getInventoryNumber(),
                device.getHostname(),
                device.getFqdn(),
                device.getPrimaryIp(),
                device.getSite(),
                owner == null ? null : owner.getId(),
                ownerFirstName,
                ownerLastName,
                department == null ? null : department.getId(),
                department == null ? device.getDepartmentName() : department.getName(),
                effectiveStatus,
                device.isAgentInstalled(),
                device.isUsbRemovableBlocked(),
                currentLoggedUser,
                device.getDiscoveredAt(),
                device.getArchivedAt(),
                snapshots,
                heartbeats,
                telemetrySamples,
                logEntries,
                fileSystemEvents
        );
    }

    public DeviceSnapshotDto toDto(DeviceSnapshot snapshot) {
        return toDto(snapshot, snapshot.getNetworkInterfaces().stream().toList(), snapshot.getLoggedInSessions().stream().toList());
    }

    public DeviceSnapshotDto toDto(DeviceSnapshot snapshot,
                                   List<NetworkInterface> networkInterfaces,
                                   List<LoggedInSession> loggedInSessions) {
        List<NetworkInterfaceDto> interfaces = networkInterfaces.stream()
                .sorted(Comparator.comparing(NetworkInterface::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toDto)
                .toList();
        List<LoggedInSessionDto> sessions = loggedInSessions.stream()
                .map(this::toDto)
                .toList();
        return new DeviceSnapshotDto(
                snapshot.getId(),
                snapshot.getVersionNo(),
                snapshot.getCollectedAt(),
                snapshot.getValidFrom(),
                snapshot.getValidTo(),
                snapshot.getHostname(),
                snapshot.getOsName(),
                snapshot.getOsVersion(),
                snapshot.getOsBuild(),
                snapshot.getOsArchitecture(),
                snapshot.getDomainName(),
                snapshot.getCurrentLoggedUser(),
                snapshot.getLastBootAt(),
                snapshot.getJavaAgentVersion(),
                interfaces,
                sessions
        );
    }

    public NetworkInterfaceDto toDto(NetworkInterface networkInterface) {
        return new NetworkInterfaceDto(
                networkInterface.getId(),
                networkInterface.getName(),
                networkInterface.getDisplayName(),
                networkInterface.getMacAddress(),
                networkInterface.getIpv4(),
                networkInterface.getIpv6(),
                networkInterface.isPrimary(),
                networkInterface.isUp()
        );
    }

    public LoggedInSessionDto toDto(LoggedInSession session) {
        return new LoggedInSessionDto(
                session.getId(),
                session.getUsername(),
                session.getDomain(),
                session.getSessionType() == null ? null : session.getSessionType().name(),
                session.getState() == null ? null : session.getState().name(),
                session.getLoginTime()
        );
    }

    public AgentHeartbeatDto toDto(AgentHeartbeat heartbeat) {
        return new AgentHeartbeatDto(
                heartbeat.getId(),
                heartbeat.getAgentVersion(),
                heartbeat.getServiceStatus() == null ? null : heartbeat.getServiceStatus().name(),
                heartbeat.getLastSeenAt(),
                heartbeat.getLastCollectAt(),
                heartbeat.getLastError()
        );
    }

    public TelemetrySampleDto toDto(TelemetrySample telemetrySample) {
        return new TelemetrySampleDto(
                telemetrySample.getId(),
                telemetrySample.getCollectedAt(),
                telemetrySample.getCpuUsagePct(),
                telemetrySample.getMemoryUsagePct(),
                telemetrySample.getDiskUsagePct(),
                telemetrySample.getProcessCount(),
                telemetrySample.getServiceCount()
        );
    }

    public FileSystemEventDto toDto(FileSystemEvent fileSystemEvent) {
        return new FileSystemEventDto(
                fileSystemEvent.getId(),
                fileSystemEvent.getOccurredAt(),
                fileSystemEvent.getEventType() == null ? null : fileSystemEvent.getEventType().name(),
                fileSystemEvent.getPath(),
                fileSystemEvent.getActorUsername(),
                fileSystemEvent.getSourceLog(),
                fileSystemEvent.getDetailsJson()
        );
    }

    public DeviceLogEntryDto toDto(DeviceLogEntry logEntry) {
        return new DeviceLogEntryDto(
                logEntry.getId(),
                logEntry.getOccurredAt(),
                logEntry.getLogSource() == null ? null : logEntry.getLogSource().name(),
                logEntry.getLevel(),
                logEntry.getEventCode(),
                logEntry.getMessage(),
                logEntry.getRawPayload()
        );
    }

    public DetectionRuleDto toDto(DetectionRule detectionRule) {
        return new DetectionRuleDto(
                detectionRule.getId(),
                detectionRule.getCode(),
                detectionRule.getName(),
                detectionRule.getDescription(),
                detectionRule.getSeverity() == null ? null : detectionRule.getSeverity().name(),
                detectionRule.getSourceType() == null ? null : detectionRule.getSourceType().name(),
                detectionRule.getConditionJson(),
                detectionRule.isEnabled()
        );
    }

    public DetectionFindingDto toDto(DetectionFinding detectionFinding) {
        return toDto(detectionFinding, List.of());
    }

    public DetectionFindingDto toDto(DetectionFinding detectionFinding, List<DetectionFindingEventDto> events) {
        return new DetectionFindingDto(
                detectionFinding.getId(),
                detectionFinding.getDevice().getId(),
                detectionFinding.getDevice().getHostname(),
                detectionFinding.getRule() == null ? null : detectionFinding.getRule().getId(),
                detectionFinding.getRule() == null ? null : detectionFinding.getRule().getCode(),
                detectionFinding.getStatus() == null ? null : detectionFinding.getStatus().name(),
                detectionFinding.getSeverity() == null ? null : detectionFinding.getSeverity().name(),
                detectionFinding.getTitle(),
                detectionFinding.getDescription(),
                detectionFinding.getFirstSeenAt(),
                detectionFinding.getLastSeenAt(),
                detectionFinding.isCreatedByAi(),
                detectionFinding.getContextJson(),
                events
        );
    }

    public DetectionFindingEventDto toDto(DetectionFindingEvent event) {
        return new DetectionFindingEventDto(
                event.getId(),
                event.getEventType() == null ? null : event.getEventType().name(),
                event.getOccurredAt(),
                event.getSourceRecordId(),
                event.getSourceLog(),
                event.getLevel(),
                event.getEventCode(),
                event.getMessage(),
                event.getPath(),
                event.getActorUsername(),
                event.getPayloadJson()
        );
    }

    public AIAnalysisRunDto toDto(AIAnalysisRun aiAnalysisRun) {
        return new AIAnalysisRunDto(
                aiAnalysisRun.getId(),
                aiAnalysisRun.getDevice().getId(),
                aiAnalysisRun.getTriggeredByUser() == null ? null : aiAnalysisRun.getTriggeredByUser().getId(),
                aiAnalysisRun.getModelName(),
                aiAnalysisRun.getPromptVersion(),
                aiAnalysisRun.getStartedAt(),
                aiAnalysisRun.getCompletedAt(),
                aiAnalysisRun.getResultSummary(),
                aiAnalysisRun.getRiskScore(),
                aiAnalysisRun.getReportJson()
        );
    }

    public RemoteHelpRequestDto toDto(RemoteHelpRequest remoteHelpRequest) {
        EndpointDevice device = remoteHelpRequest.getDevice();
        String remoteAssistanceTarget = firstNonBlank(
                device.getHostname(),
                device.getPrimaryIp(),
                device.getFqdn()
        );
        return new RemoteHelpRequestDto(
                remoteHelpRequest.getId(),
                device.getId(),
                device.getHostname(),
                device.getFqdn(),
                device.getPrimaryIp(),
                remoteAssistanceTarget,
                buildRemoteAssistanceUri(remoteAssistanceTarget),
                remoteHelpRequest.getRequestedByUsername(),
                remoteHelpRequest.getRequestedByDisplayName(),
                remoteHelpRequest.getMessage(),
                remoteHelpRequest.getStatus() == null ? null : remoteHelpRequest.getStatus().name(),
                remoteHelpRequest.getRequestedAt(),
                remoteHelpRequest.getAcceptedByUser() == null ? null : remoteHelpRequest.getAcceptedByUser().getId(),
                remoteHelpRequest.getAcceptedAt()
        );
    }

    public RemoteSessionDto toDto(RemoteSession remoteSession) {
        EndpointDevice device = remoteSession.getDevice();
        String remoteAssistanceTarget = firstNonBlank(
                device.getHostname(),
                device.getPrimaryIp(),
                device.getFqdn()
        );
        return new RemoteSessionDto(
                remoteSession.getId(),
                remoteSession.getHelpRequest() == null ? null : remoteSession.getHelpRequest().getId(),
                device.getId(),
                device.getHostname(),
                device.getPrimaryIp(),
                remoteAssistanceTarget,
                buildRemoteAssistanceUri(remoteAssistanceTarget),
                remoteSession.getAdminUser().getId(),
                remoteSession.getSessionType() == null ? null : remoteSession.getSessionType().name(),
                remoteSession.getProvider() == null ? null : remoteSession.getProvider().name(),
                remoteSession.getStatus() == null ? null : remoteSession.getStatus().name(),
                remoteSession.getStartedAt(),
                remoteSession.getEndedAt()
        );
    }

    private String buildRemoteAssistanceUri(String targetHost) {
        if (targetHost == null || targetHost.isBlank()) {
            return null;
        }
        return "evidencecyber-ra://open?host=" + URLEncoder.encode(targetHost, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public ControlApprovalDto toDto(ControlApproval controlApproval) {
        return new ControlApprovalDto(
                controlApproval.getId(),
                controlApproval.getRemoteSession().getId(),
                controlApproval.getRequestedAt(),
                controlApproval.getDecision() == null ? null : controlApproval.getDecision().name(),
                controlApproval.getDecidedByUsername(),
                controlApproval.getDecidedAt(),
                controlApproval.getNote()
        );
    }

    public CommandRequestDto toDto(CommandRequest commandRequest) {
        return toDto(commandRequest, null);
    }

    public CommandRequestDto toDto(CommandRequest commandRequest, CommandExecution latestExecution) {
        return new CommandRequestDto(
                commandRequest.getId(),
                commandRequest.getDevice().getId(),
                commandRequest.getDevice().getHostname(),
                commandRequest.getRequestedByUser().getId(),
                commandRequest.getRequestedByUser().getAdUsername(),
                commandRequest.getCommandType() == null ? null : commandRequest.getCommandType().name(),
                commandRequest.getStatus() == null ? null : commandRequest.getStatus().name(),
                sanitizeCommandPayload(commandRequest.getPayloadJson()),
                commandRequest.getCreatedAt(),
                latestExecution == null ? null : toDto(latestExecution)
        );
    }

    public CommandExecutionDto toDto(CommandExecution commandExecution) {
        return new CommandExecutionDto(
                commandExecution.getId(),
                commandExecution.getCommandRequest().getId(),
                commandExecution.getAgentHeartbeat() == null ? null : commandExecution.getAgentHeartbeat().getId(),
                commandExecution.getStartedAt(),
                commandExecution.getFinishedAt(),
                commandExecution.getExitCode(),
                commandExecution.getResultSummary(),
                commandExecution.getErrorMessage(),
                commandExecution.getResultJson()
        );
    }

    public AgentPendingCommandDto toPendingCommandDto(CommandRequest commandRequest) {
        return new AgentPendingCommandDto(
                commandRequest.getId(),
                commandRequest.getCommandType() == null ? null : commandRequest.getCommandType().name(),
                commandRequest.getPayloadJson(),
                commandRequest.getCreatedAt()
        );
    }

    private Map<String, Object> sanitizeCommandPayload(Map<String, Object> payloadJson) {
        if (payloadJson == null) {
            return null;
        }
        return sanitizeMap(payloadJson);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeMap(Map<String, Object> values) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && isSensitiveKey(key)) {
                sanitized.put(key, maskSensitiveValue(value));
            } else if (value instanceof Map<?, ?> nestedMap) {
                sanitized.put(key, sanitizeMap((Map<String, Object>) nestedMap));
            } else if (value instanceof List<?> nestedList) {
                sanitized.put(key, sanitizeList(nestedList));
            } else {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    private List<Object> sanitizeList(List<?> values) {
        List<Object> sanitized = new ArrayList<>(values.size());
        for (Object value : values) {
            if (value instanceof Map<?, ?> nestedMap) {
                sanitized.add(sanitizeMap((Map<String, Object>) nestedMap));
            } else if (value instanceof List<?> nestedList) {
                sanitized.add(sanitizeList(nestedList));
            } else {
                sanitized.add(value);
            }
        }
        return sanitized;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase();
        return normalized.contains("password") || normalized.contains("secret");
    }

    private Object maskSensitiveValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? stringValue : "********";
    }

    public AuditLogDto toDto(AuditLog auditLog) {
        return new AuditLogDto(
                auditLog.getId(),
                auditLog.getActorUser() == null ? null : auditLog.getActorUser().getId(),
                auditLog.getActorSource() == null ? null : auditLog.getActorSource().name(),
                auditLog.getActionType(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getResult() == null ? null : auditLog.getResult().name(),
                auditLog.getCreatedAt(),
                auditLog.getDetailsJson()
        );
    }
}
