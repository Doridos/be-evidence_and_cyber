package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.*;
import cz.fel.cvut.beevidence_and_cyber.dto.*;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

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
                                 List<DeviceSnapshotDto> snapshots,
                                 List<AgentHeartbeatDto> heartbeats,
                                 List<TelemetrySampleDto> telemetrySamples,
                                 List<DeviceLogEntryDto> logEntries,
                                 List<FileSystemEventDto> fileSystemEvents) {
        return new DeviceDetailDto(
                device.getId(),
                device.getAssetTag(),
                device.getHostname(),
                device.getFqdn(),
                device.getPrimaryIp(),
                device.getSite(),
                device.getStatus().name(),
                device.isAgentInstalled(),
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
        return new DetectionFindingDto(
                detectionFinding.getId(),
                detectionFinding.getDevice().getId(),
                detectionFinding.getRule() == null ? null : detectionFinding.getRule().getId(),
                detectionFinding.getStatus() == null ? null : detectionFinding.getStatus().name(),
                detectionFinding.getSeverity() == null ? null : detectionFinding.getSeverity().name(),
                detectionFinding.getTitle(),
                detectionFinding.getDescription(),
                detectionFinding.getFirstSeenAt(),
                detectionFinding.getLastSeenAt(),
                detectionFinding.isCreatedByAi()
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
                aiAnalysisRun.getRiskScore()
        );
    }

    public RemoteHelpRequestDto toDto(RemoteHelpRequest remoteHelpRequest) {
        return new RemoteHelpRequestDto(
                remoteHelpRequest.getId(),
                remoteHelpRequest.getDevice().getId(),
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
        return new RemoteSessionDto(
                remoteSession.getId(),
                remoteSession.getHelpRequest().getId(),
                remoteSession.getAdminUser().getId(),
                remoteSession.getSessionType() == null ? null : remoteSession.getSessionType().name(),
                remoteSession.getProvider() == null ? null : remoteSession.getProvider().name(),
                remoteSession.getStatus() == null ? null : remoteSession.getStatus().name(),
                remoteSession.getStartedAt(),
                remoteSession.getEndedAt()
        );
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
        return new CommandRequestDto(
                commandRequest.getId(),
                commandRequest.getDevice().getId(),
                commandRequest.getRequestedByUser().getId(),
                commandRequest.getCommandType() == null ? null : commandRequest.getCommandType().name(),
                commandRequest.getStatus() == null ? null : commandRequest.getStatus().name(),
                commandRequest.getPayloadJson(),
                commandRequest.getCreatedAt()
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
                commandExecution.getErrorMessage()
        );
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
