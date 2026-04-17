package cz.fel.cvut.beevidence_and_cyber.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.fel.cvut.beevidence_and_cyber.dao.AIAnalysisRun;
import cz.fel.cvut.beevidence_and_cyber.dao.AgentHeartbeat;
import cz.fel.cvut.beevidence_and_cyber.dao.CommandExecution;
import cz.fel.cvut.beevidence_and_cyber.dao.CommandRequest;
import cz.fel.cvut.beevidence_and_cyber.dao.DetectionFinding;
import cz.fel.cvut.beevidence_and_cyber.dao.DetectionFindingEvent;
import cz.fel.cvut.beevidence_and_cyber.dao.DeviceLogEntry;
import cz.fel.cvut.beevidence_and_cyber.dao.DeviceSnapshot;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dao.FileSystemEvent;
import cz.fel.cvut.beevidence_and_cyber.dao.RemoteSession;
import cz.fel.cvut.beevidence_and_cyber.dao.TelemetrySample;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.AIAnalysisRunDto;
import cz.fel.cvut.beevidence_and_cyber.dto.AIAnalysisRunRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.AIChatMessageDto;
import cz.fel.cvut.beevidence_and_cyber.dto.AIChatRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.AIChatResponseDto;
import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import cz.fel.cvut.beevidence_and_cyber.exception.BadRequestException;
import cz.fel.cvut.beevidence_and_cyber.repository.AIAnalysisRunRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.AgentHeartbeatRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.CommandExecutionRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.CommandRequestRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionFindingEventRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionFindingRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceLogEntryRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceSnapshotRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.FileSystemEventRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.LoggedInSessionRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.NetworkInterfaceRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.RemoteSessionRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.TelemetrySampleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private static final int LOG_LIMIT = 200;
    private static final int FILE_EVENT_LIMIT = 200;
    private static final int TELEMETRY_LIMIT = 120;
    private static final int HEARTBEAT_LIMIT = 120;
    private static final int COMMAND_LIMIT = 80;
    private static final int FINDING_LIMIT = 40;
    private static final int FINDING_EVENT_LIMIT = 6;
    private static final int SNAPSHOT_LIMIT = 12;
    private static final int REMOTE_SESSION_LIMIT = 40;
    private static final Duration AI_ENDPOINT_TIMEOUT = Duration.ofMinutes(5);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Map<String, Object> ANALYSIS_SCHEMA = createAnalysisSchema();

    private final AIAnalysisRunRepository aiAnalysisRunRepository;
    private final DetectionFindingRepository detectionFindingRepository;
    private final DetectionFindingEventRepository detectionFindingEventRepository;
    private final DeviceLogEntryRepository deviceLogEntryRepository;
    private final FileSystemEventRepository fileSystemEventRepository;
    private final DeviceSnapshotRepository deviceSnapshotRepository;
    private final NetworkInterfaceRepository networkInterfaceRepository;
    private final LoggedInSessionRepository loggedInSessionRepository;
    private final TelemetrySampleRepository telemetrySampleRepository;
    private final AgentHeartbeatRepository agentHeartbeatRepository;
    private final RemoteSessionRepository remoteSessionRepository;
    private final CommandRequestRepository commandRequestRepository;
    private final CommandExecutionRepository commandExecutionRepository;
    private final DeviceService deviceService;
    private final ApiMapper apiMapper;
    private final AuditService auditService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public List<AIAnalysisRunDto> getAllAiRuns() {
        return aiAnalysisRunRepository.findAllByOrderByStartedAtDesc().stream()
                .map(apiMapper::toDto)
                .toList();
    }

    @Transactional
    public AIAnalysisRunDto createAiRun(AIAnalysisRunRequest request, User actor) {
        EndpointDevice device = deviceService.findDevice(request.deviceId());
        validateWindow(request.from(), request.to());

        LocalDateTime startedAt = LocalDateTime.now();
        DeviceAiContext context = collectContext(device, request.from(), request.to());
        String prompt = buildAnalysisPrompt(device, request.from(), request.to(), request.analystQuestion(), context.payload());
        Map<String, Object> report = callStructuredOllama(
                request.endpointUrl(),
                request.modelName(),
                List.of(Map.of("role", "user", "content", prompt)),
                ANALYSIS_SCHEMA
        );

        AIAnalysisRun run = new AIAnalysisRun();
        run.setDevice(device);
        run.setTriggeredByUser(actor);
        run.setModelName(request.modelName());
        run.setPromptVersion(AiAnalysisPrompts.VERSION);
        run.setStartedAt(startedAt);
        run.setCompletedAt(LocalDateTime.now());
        run.setResultSummary(firstNonBlank(asText(report.get("summary")), asText(report.get("overallAssessment")), asText(report.get("headline"))));
        run.setRiskScore(toRiskScore(report.get("riskScore")));
        run.setReportJson(report);
        AIAnalysisRun saved = aiAnalysisRunRepository.save(run);

        auditService.log(actor, ActorSourceEnum.WEB, "CREATE_AI_ANALYSIS_RUN", "AI_ANALYSIS_RUN", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of(
                        "deviceId", device.getId().toString(),
                        "modelName", request.modelName(),
                        "timeFrom", request.from().toString(),
                        "timeTo", request.to().toString()
                ));

        return apiMapper.toDto(saved);
    }

    public AIChatResponseDto chat(UUID deviceId, AIChatRequest request, User actor) {
        EndpointDevice device = deviceService.findDevice(deviceId);
        validateWindow(request.from(), request.to());

        DeviceAiContext context = collectContext(device, request.from(), request.to());
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", buildChatContextPrompt(device, request.from(), request.to(), context.payload())));
        if (request.history() != null) {
            request.history().stream()
                    .filter(Objects::nonNull)
                    .map(this::toChatMessage)
                    .forEach(messages::add);
        }
        messages.add(Map.of("role", "user", "content", request.question().trim()));

        String answer = callTextOllama(request.endpointUrl(), request.modelName(), messages);
        auditService.log(actor, ActorSourceEnum.WEB, "CHAT_WITH_DEVICE_AI", "DEVICE", device.getId(), AuditResultEnum.SUCCESS,
                Map.of(
                        "modelName", request.modelName(),
                        "timeFrom", request.from().toString(),
                        "timeTo", request.to().toString()
                ));

        return new AIChatResponseDto(answer, request.modelName(), AiAnalysisPrompts.VERSION, LocalDateTime.now());
    }

    private Map<String, Object> toChatMessage(AIChatMessageDto message) {
        String role = message.role().trim().toLowerCase(Locale.ROOT);
        if (!role.equals("user") && !role.equals("assistant")) {
            throw new BadRequestException("Chat historie může obsahovat pouze role user nebo assistant.");
        }
        return Map.of("role", role, "content", message.content().trim());
    }

    private void validateWindow(LocalDateTime from, LocalDateTime to) {
        if (from.isAfter(to)) {
            throw new BadRequestException("Počátek období musí být dříve než jeho konec.");
        }
        if (Duration.between(from, to).toDays() > 31) {
            throw new BadRequestException("AI analýza aktuálně podporuje maximálně 31 dní v jednom běhu.");
        }
    }

    private DeviceAiContext collectContext(EndpointDevice device, LocalDateTime from, LocalDateTime to) {
        List<DeviceLogEntry> logEntries = truncateTail(
                deviceLogEntryRepository.findByDeviceAndOccurredAtBetweenOrderByOccurredAtAsc(device, from, to),
                LOG_LIMIT
        );
        List<FileSystemEvent> fileSystemEvents = truncateTail(
                fileSystemEventRepository.findByDeviceAndOccurredAtBetweenOrderByOccurredAtAsc(device, from, to),
                FILE_EVENT_LIMIT
        );
        List<TelemetrySample> telemetrySamples = truncateTail(
                telemetrySampleRepository.findByDeviceAndCollectedAtBetweenOrderByCollectedAtAsc(device, from, to),
                TELEMETRY_LIMIT
        );

        List<AgentHeartbeat> heartbeats = agentHeartbeatRepository.findByDeviceOrderByLastSeenAtDesc(device).stream()
                .filter(item -> isBetween(item.getLastSeenAt(), from, to))
                .sorted(Comparator.comparing(AgentHeartbeat::getLastSeenAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.collectingAndThen(Collectors.toList(), items -> truncateTail(items, HEARTBEAT_LIMIT)));

        List<RemoteSession> remoteSessions = remoteSessionRepository.findByDeviceOrderByStartedAtDesc(device).stream()
                .filter(item -> overlaps(item.getStartedAt(), item.getEndedAt(), from, to))
                .sorted(Comparator.comparing(RemoteSession::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.collectingAndThen(Collectors.toList(), items -> truncateTail(items, REMOTE_SESSION_LIMIT)));

        List<CommandRequest> commandRequests = commandRequestRepository.findByDeviceOrderByCreatedAtDesc(device).stream()
                .filter(item -> isBetween(item.getCreatedAt(), from, to))
                .sorted(Comparator.comparing(CommandRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.collectingAndThen(Collectors.toList(), items -> truncateTail(items, COMMAND_LIMIT)));

        List<DetectionFinding> findings = detectionFindingRepository.findByDeviceOrderByLastSeenAtDesc(device).stream()
                .filter(item -> overlaps(item.getFirstSeenAt(), item.getLastSeenAt(), from, to))
                .sorted(Comparator.comparing(DetectionFinding::getLastSeenAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.collectingAndThen(Collectors.toList(), items -> truncateTail(items, FINDING_LIMIT)));

        List<DeviceSnapshot> snapshots = deviceSnapshotRepository.findByDeviceOrderByCollectedAtDesc(device).stream()
                .filter(item -> isBetween(item.getCollectedAt(), from, to))
                .sorted(Comparator.comparing(DeviceSnapshot::getCollectedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.collectingAndThen(Collectors.toList(), items -> truncateTail(items, SNAPSHOT_LIMIT)));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device", buildDeviceSummary(device));
        payload.put("analysisWindow", Map.of(
                "from", from,
                "to", to,
                "timezone", "Europe/Prague"
        ));
        payload.put("summaryMetrics", buildSummaryMetrics(logEntries, fileSystemEvents, telemetrySamples, heartbeats, remoteSessions, commandRequests, findings));
        payload.put("heartbeats", heartbeats.stream().map(this::toHeartbeatContext).toList());
        payload.put("telemetry", telemetrySamples.stream().map(this::toTelemetryContext).toList());
        payload.put("remoteSessions", remoteSessions.stream().map(this::toRemoteSessionContext).toList());
        payload.put("commands", commandRequests.stream().map(this::toCommandContext).toList());
        payload.put("snapshots", snapshots.stream().map(this::toSnapshotContext).toList());
        payload.put("findings", findings.stream().map(this::toFindingContext).toList());
        payload.put("logs", logEntries.stream().map(this::toLogContext).toList());
        payload.put("fileSystemEvents", fileSystemEvents.stream().map(this::toFileEventContext).toList());
        payload.put("truncation", Map.of(
                "logs", buildTruncation(logEntries, deviceLogEntryRepository.findByDeviceAndOccurredAtBetweenOrderByOccurredAtAsc(device, from, to).size(), LOG_LIMIT),
                "fileSystemEvents", buildTruncation(fileSystemEvents, fileSystemEventRepository.findByDeviceAndOccurredAtBetweenOrderByOccurredAtAsc(device, from, to).size(), FILE_EVENT_LIMIT),
                "telemetry", buildTruncation(telemetrySamples, telemetrySampleRepository.findByDeviceAndCollectedAtBetweenOrderByCollectedAtAsc(device, from, to).size(), TELEMETRY_LIMIT)
        ));
        return new DeviceAiContext(payload);
    }

    private Map<String, Object> buildDeviceSummary(EndpointDevice device) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", device.getId());
        summary.put("hostname", device.getHostname());
        summary.put("fqdn", device.getFqdn());
        summary.put("primaryIp", device.getPrimaryIp());
        summary.put("site", device.getSite());
        summary.put("status", device.getStatus() == null ? null : device.getStatus().name());
        summary.put("agentInstalled", device.isAgentInstalled());
        summary.put("usbRemovableBlocked", device.isUsbRemovableBlocked());
        summary.put("owner", firstNonBlank(
                joinNonBlank(" ", device.getOwnerFirstName(), device.getOwnerLastName()),
                device.getOwner() == null ? null : joinNonBlank(" ", device.getOwner().getFirstName(), device.getOwner().getLastName())
        ));
        summary.put("discoveredAt", device.getDiscoveredAt());
        return summary;
    }

    private Map<String, Object> buildSummaryMetrics(List<DeviceLogEntry> logs,
                                                    List<FileSystemEvent> fileEvents,
                                                    List<TelemetrySample> telemetry,
                                                    List<AgentHeartbeat> heartbeats,
                                                    List<RemoteSession> remoteSessions,
                                                    List<CommandRequest> commands,
                                                    List<DetectionFinding> findings) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("logCount", logs.size());
        metrics.put("fileEventCount", fileEvents.size());
        metrics.put("heartbeatCount", heartbeats.size());
        metrics.put("telemetryCount", telemetry.size());
        metrics.put("remoteSessionCount", remoteSessions.size());
        metrics.put("commandCount", commands.size());
        metrics.put("findingCount", findings.size());
        metrics.put("highSeverityFindingCount", findings.stream()
                .filter(item -> item.getSeverity() != null && switch (item.getSeverity()) {
                    case HIGH -> true;
                    default -> false;
                })
                .count());
        metrics.put("peakCpuPct", telemetry.stream().map(TelemetrySample::getCpuUsagePct).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null));
        metrics.put("peakMemoryPct", telemetry.stream().map(TelemetrySample::getMemoryUsagePct).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null));
        metrics.put("peakDiskPct", telemetry.stream().map(TelemetrySample::getDiskUsagePct).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null));
        metrics.put("lastHeartbeatAt", heartbeats.isEmpty() ? null : heartbeats.get(heartbeats.size() - 1).getLastSeenAt());
        return metrics;
    }

    private Map<String, Object> buildTruncation(List<?> retained, int total, int limit) {
        return Map.of(
                "retained", retained.size(),
                "total", total,
                "truncated", Math.max(total - limit, 0)
        );
    }

    private Map<String, Object> toLogContext(DeviceLogEntry entry) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("occurredAt", entry.getOccurredAt());
        payload.put("logSource", entry.getLogSource() == null ? null : entry.getLogSource().name());
        payload.put("level", entry.getLevel());
        payload.put("eventCode", entry.getEventCode());
        payload.put("message", trimText(entry.getMessage(), 600));
        return payload;
    }

    private Map<String, Object> toFileEventContext(FileSystemEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("occurredAt", event.getOccurredAt());
        payload.put("eventType", event.getEventType() == null ? null : event.getEventType().name());
        payload.put("path", event.getPath());
        payload.put("actorUsername", event.getActorUsername());
        payload.put("sourceLog", event.getSourceLog());
        payload.put("details", event.getDetailsJson());
        return payload;
    }

    private Map<String, Object> toTelemetryContext(TelemetrySample sample) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("collectedAt", sample.getCollectedAt());
        payload.put("cpuUsagePct", sample.getCpuUsagePct());
        payload.put("memoryUsagePct", sample.getMemoryUsagePct());
        payload.put("diskUsagePct", sample.getDiskUsagePct());
        payload.put("processCount", sample.getProcessCount());
        payload.put("serviceCount", sample.getServiceCount());
        return payload;
    }

    private Map<String, Object> toHeartbeatContext(AgentHeartbeat heartbeat) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lastSeenAt", heartbeat.getLastSeenAt());
        payload.put("lastCollectAt", heartbeat.getLastCollectAt());
        payload.put("serviceStatus", heartbeat.getServiceStatus() == null ? null : heartbeat.getServiceStatus().name());
        payload.put("agentVersion", heartbeat.getAgentVersion());
        payload.put("lastError", heartbeat.getLastError());
        return payload;
    }

    private Map<String, Object> toRemoteSessionContext(RemoteSession session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("startedAt", session.getStartedAt());
        payload.put("endedAt", session.getEndedAt());
        payload.put("status", session.getStatus() == null ? null : session.getStatus().name());
        payload.put("sessionType", session.getSessionType() == null ? null : session.getSessionType().name());
        payload.put("provider", session.getProvider() == null ? null : session.getProvider().name());
        payload.put("adminUserId", session.getAdminUser() == null ? null : session.getAdminUser().getId());
        payload.put("helpRequestId", session.getHelpRequest() == null ? null : session.getHelpRequest().getId());
        return payload;
    }

    private Map<String, Object> toCommandContext(CommandRequest request) {
        CommandExecution latestExecution = commandExecutionRepository.findTopByCommandRequestOrderByStartedAtDescIdDesc(request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("createdAt", request.getCreatedAt());
        payload.put("commandType", request.getCommandType() == null ? null : request.getCommandType().name());
        payload.put("status", request.getStatus() == null ? null : request.getStatus().name());
        payload.put("payload", request.getPayloadJson());
        if (latestExecution == null) {
            payload.put("latestExecution", null);
        } else {
            Map<String, Object> executionPayload = new LinkedHashMap<>();
            executionPayload.put("startedAt", latestExecution.getStartedAt());
            executionPayload.put("finishedAt", latestExecution.getFinishedAt());
            executionPayload.put("exitCode", latestExecution.getExitCode());
            executionPayload.put("resultSummary", latestExecution.getResultSummary());
            executionPayload.put("errorMessage", latestExecution.getErrorMessage());
            executionPayload.put("resultJson", latestExecution.getResultJson());
            payload.put("latestExecution", executionPayload);
        }
        return payload;
    }

    private Map<String, Object> toSnapshotContext(DeviceSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", snapshot.getId());
        payload.put("versionNo", snapshot.getVersionNo());
        payload.put("collectedAt", snapshot.getCollectedAt());
        payload.put("hostname", snapshot.getHostname());
        payload.put("osName", snapshot.getOsName());
        payload.put("osVersion", snapshot.getOsVersion());
        payload.put("osBuild", snapshot.getOsBuild());
        payload.put("osArchitecture", snapshot.getOsArchitecture());
        payload.put("domainName", snapshot.getDomainName());
        payload.put("currentLoggedUser", snapshot.getCurrentLoggedUser());
        payload.put("lastBootAt", snapshot.getLastBootAt());
        payload.put("javaAgentVersion", snapshot.getJavaAgentVersion());
        payload.put("networkInterfaces", networkInterfaceRepository.findBySnapshot(snapshot).stream().map(apiMapper::toDto).toList());
        payload.put("loggedInSessions", loggedInSessionRepository.findBySnapshot(snapshot).stream().map(apiMapper::toDto).toList());
        return payload;
    }

    private Map<String, Object> toFindingContext(DetectionFinding finding) {
        List<Map<String, Object>> events = detectionFindingEventRepository.findByFindingOrderByOccurredAtAscIdAsc(finding).stream()
                .limit(FINDING_EVENT_LIMIT)
                .map(this::toFindingEventContext)
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", finding.getId());
        payload.put("ruleId", finding.getRule() == null ? null : finding.getRule().getId());
        payload.put("ruleCode", finding.getRule() == null ? null : finding.getRule().getCode());
        payload.put("status", finding.getStatus() == null ? null : finding.getStatus().name());
        payload.put("severity", finding.getSeverity() == null ? null : finding.getSeverity().name());
        payload.put("title", finding.getTitle());
        payload.put("description", trimText(finding.getDescription(), 800));
        payload.put("firstSeenAt", finding.getFirstSeenAt());
        payload.put("lastSeenAt", finding.getLastSeenAt());
        payload.put("createdByAi", finding.isCreatedByAi());
        payload.put("contextJson", finding.getContextJson());
        payload.put("events", events);
        return payload;
    }

    private Map<String, Object> toFindingEventContext(DetectionFindingEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", event.getEventType() == null ? null : event.getEventType().name());
        payload.put("occurredAt", event.getOccurredAt());
        payload.put("sourceRecordId", event.getSourceRecordId());
        payload.put("sourceLog", event.getSourceLog());
        payload.put("level", event.getLevel());
        payload.put("eventCode", event.getEventCode());
        payload.put("message", trimText(event.getMessage(), 400));
        payload.put("path", event.getPath());
        payload.put("actorUsername", event.getActorUsername());
        payload.put("payloadJson", event.getPayloadJson());
        return payload;
    }

    private Map<String, Object> callStructuredOllama(String endpointUrl,
                                                     String modelName,
                                                     List<Map<String, Object>> messages,
                                                     Map<String, Object> schema) {
        String content = callOllama(endpointUrl, modelName, messages, schema);
        try {
            return OBJECT_MAPPER.readValue(content, MAP_TYPE);
        } catch (IOException exception) {
            throw new BadRequestException("AI endpoint vratil nevalidni JSON odpoved: " + exception.getMessage());
        }
    }

    private String callTextOllama(String endpointUrl, String modelName, List<Map<String, Object>> messages) {
        return callOllama(endpointUrl, modelName, messages, null);
    }

    private String callOllama(String endpointUrl,
                              String modelName,
                              List<Map<String, Object>> userMessages,
                              Map<String, Object> formatSchema) {
        URI uri;
        try {
            uri = URI.create(endpointUrl);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Endpoint AI neni validni URL.");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", AiAnalysisPrompts.SYSTEM_PROMPT));
        messages.addAll(userMessages);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("options", Map.of("temperature", 0.2));
        if (formatSchema != null) {
            body.put("format", formatSchema);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(AI_ENDPOINT_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new BadRequestException("AI endpoint vratil chybu " + response.statusCode() + ": " + extractErrorBody(response.body()));
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode contentNode = root.path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull() || contentNode.asText().isBlank()) {
                contentNode = root.path("response");
            }
            String content = contentNode.asText("");
            if (content.isBlank()) {
                throw new BadRequestException("AI endpoint vratil prazdnou odpoved.");
            }
            return content.trim();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Spojeni s AI endpointem bylo preruseno.");
        } catch (IOException exception) {
            throw new BadRequestException(buildConnectivityErrorMessage(uri, exception));
        }
    }

    private boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("localhost")
                || normalized.equals("127.0.0.1")
                || normalized.equals("::1")
                || normalized.equals("[::1]");
    }

    private String buildConnectivityErrorMessage(URI uri, IOException exception) {
        String details = describeException(exception);
        if (isLoopbackHost(uri.getHost())) {
            return "Nepodarilo se spojit s AI endpointem: " + details
                    + ". Backend pravdepodobne bezi v Dockeru a localhost tedy miri do kontejneru. Zkus endpoint typu http://host.docker.internal:11434/api/chat.";
        }
        return "Nepodarilo se spojit s AI endpointem: " + details;
    }

    private String describeException(Exception exception) {
        String message = exception.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        return exception.getClass().getSimpleName();
    }

    private String buildAnalysisPrompt(EndpointDevice device,
                                       LocalDateTime from,
                                       LocalDateTime to,
                                       String analystQuestion,
                                       Map<String, Object> context) {
        return """
                Proved korelovanou bezpecnostni analyzu koncove stanice a vrat pouze validni JSON podle pozadovaneho schematu.

                Zarizeni: %s
                Obdobi od: %s
                Obdobi do: %s
                Dodatecne zadani analytika: %s

                Vystup:
                - trafficLight nastav na GREEN, AMBER nebo RED,
                - riskScore dej v rozsahu 0-100,
                - headline ma byt jedna kratka veta,
                - summary a overallAssessment pis cesky a konkretne,
                - timeline obsahuje nejdulezitejsi udalosti v case,
                - topRisks ma obsahovat jen skutecne relevantni rizika,
                - recommendations serad podle priority NOW, SOON, LATER,
                - followUpQuestions vypln jen pokud chybi dulezita data nebo je potreba dalsi kontrola.

                Pokud jsou data klidna a bez zjevne anomalie, vrat GREEN a nevytvarej umele podezreni.

                Kontext ve formatu JSON:
                %s
                """.formatted(
                firstNonBlank(device.getHostname(), device.getPrimaryIp(), device.getFqdn()),
                formatForPrompt(from),
                formatForPrompt(to),
                firstNonBlank(analystQuestion, "Bez doplnujici otazky."),
                toPrettyJson(context)
        );
    }

    private String buildChatContextPrompt(EndpointDevice device,
                                          LocalDateTime from,
                                          LocalDateTime to,
                                          Map<String, Object> context) {
        return """
                Toto je kontext zarizeni, nad kterym budes odpovidat na dalsi dotazy analytika.
                Drz se pouze techto dat, odpovidej cesky a kdyz neco v datech neni, rekni to otevrene.

                Zarizeni: %s
                Obdobi od: %s
                Obdobi do: %s

                Kontext JSON:
                %s
                """.formatted(
                firstNonBlank(device.getHostname(), device.getPrimaryIp(), device.getFqdn()),
                formatForPrompt(from),
                formatForPrompt(to),
                toPrettyJson(context)
        );
    }

    private String toPrettyJson(Map<String, Object> context) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(context);
        } catch (IOException exception) {
            throw new IllegalStateException("Nepodarilo se serializovat AI kontext.", exception);
        }
    }

    private boolean overlaps(LocalDateTime startedAt, LocalDateTime endedAt, LocalDateTime from, LocalDateTime to) {
        LocalDateTime effectiveEnd = endedAt == null ? startedAt : endedAt;
        if (startedAt == null) {
            return false;
        }
        if (effectiveEnd == null) {
            effectiveEnd = startedAt;
        }
        return !startedAt.isAfter(to) && !effectiveEnd.isBefore(from);
    }

    private boolean isBetween(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        return value != null && !value.isBefore(from) && !value.isAfter(to);
    }

    private <T> List<T> truncateTail(List<T> items, int limit) {
        if (items.size() <= limit) {
            return items;
        }
        return items.subList(items.size() - limit, items.size());
    }

    private String formatForPrompt(LocalDateTime value) {
        return value.format(DATE_TIME_FORMATTER);
    }

    private String extractErrorBody(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            String error = root.path("error").asText("");
            return error.isBlank() ? body : error;
        } catch (IOException exception) {
            return body;
        }
    }

    private BigDecimal toRiskScore(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal numeric = new BigDecimal(String.valueOf(value));
            if (numeric.compareTo(BigDecimal.ZERO) < 0) {
                return BigDecimal.ZERO;
            }
            if (numeric.compareTo(BigDecimal.valueOf(100)) > 0) {
                return BigDecimal.valueOf(100);
            }
            return numeric.setScale(0, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private String trimText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String joinNonBlank(String delimiter, String... values) {
        return java.util.Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(delimiter));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static Map<String, Object> createAnalysisSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("trafficLight", "riskScore", "headline", "summary", "overallAssessment", "timeline", "topRisks", "recommendations", "followUpQuestions", "confidence"),
                "properties", Map.of(
                        "trafficLight", Map.of("type", "string", "enum", List.of("GREEN", "AMBER", "RED")),
                        "riskScore", Map.of("type", "number"),
                        "headline", Map.of("type", "string"),
                        "summary", Map.of("type", "string"),
                        "overallAssessment", Map.of("type", "string"),
                        "timeline", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "required", List.of("time", "title", "severity", "source", "description", "reasoning"),
                                        "properties", Map.of(
                                                "time", Map.of("type", "string"),
                                                "title", Map.of("type", "string"),
                                                "severity", Map.of("type", "string", "enum", List.of("INFO", "LOW", "MEDIUM", "HIGH")),
                                                "source", Map.of("type", "string"),
                                                "description", Map.of("type", "string"),
                                                "reasoning", Map.of("type", "string")
                                        )
                                )
                        ),
                        "topRisks", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "required", List.of("title", "severity", "whyItMatters", "relatedRuleCodes"),
                                        "properties", Map.of(
                                                "title", Map.of("type", "string"),
                                                "severity", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH")),
                                                "whyItMatters", Map.of("type", "string"),
                                                "relatedRuleCodes", Map.of(
                                                        "type", "array",
                                                        "items", Map.of("type", "string")
                                                )
                                        )
                                )
                        ),
                        "recommendations", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "required", List.of("priority", "action", "reason"),
                                        "properties", Map.of(
                                                "priority", Map.of("type", "string", "enum", List.of("NOW", "SOON", "LATER")),
                                                "action", Map.of("type", "string"),
                                                "reason", Map.of("type", "string")
                                        )
                                )
                        ),
                        "followUpQuestions", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        ),
                        "confidence", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH"))
                )
        );
    }

    private record DeviceAiContext(Map<String, Object> payload) {
    }
}
