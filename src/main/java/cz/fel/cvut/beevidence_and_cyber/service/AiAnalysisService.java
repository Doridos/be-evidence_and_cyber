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
    /**
     * Soft upper bound on the serialised context JSON in characters.
     * At ~3.5 chars/token this keeps the payload under ~68 k tokens,
     * leaving ~32 k tokens of headroom for the system prompt, analysis
     * prompt template and the model's response within a 100 k token limit.
     */
    private static final int MAX_CONTEXT_CHARS = 240_000;
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
                request.authToken(),
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

        String answer = callTextOllama(request.endpointUrl(), request.modelName(), request.authToken(), messages);
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
        trimContextToCharBudget(payload);
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

    /**
     * Progressively trims the most verbose list-valued context keys (oldest
     * items first, since all lists are already sorted ascending by time) until
     * the serialised payload fits within {@link #MAX_CONTEXT_CHARS}.
     * <p>
     * The trimming order prioritises the noisiest data sources so that the most
     * security-relevant structured data (findings, snapshots) is preserved as
     * long as possible.
     */
    private void trimContextToCharBudget(Map<String, Object> payload) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(payload);
            if (json.length() <= MAX_CONTEXT_CHARS) {
                return;
            }
            // Keys in order of trim priority — noisiest / least critical first
            String[] trimKeys = {"logs", "fileSystemEvents", "telemetry", "heartbeats", "remoteSessions", "commands", "snapshots", "findings"};
            for (int pass = 0; pass < 30; pass++) {
                // Pick the longest remaining list
                String targetKey = null;
                int targetSize = 0;
                for (String key : trimKeys) {
                    Object val = payload.get(key);
                    if (val instanceof List<?> list && list.size() > targetSize) {
                        targetSize = list.size();
                        targetKey = key;
                    }
                }
                if (targetKey == null || targetSize <= 1) {
                    break; // nothing left to trim
                }
                // Reduce by 25 %, keeping the newest items (tail of the list)
                List<?> list = (List<?>) payload.get(targetKey);
                int newSize = Math.max(1, (int) Math.ceil(list.size() * 0.75));
                payload.put(targetKey, list.subList(list.size() - newSize, list.size()));

                json = OBJECT_MAPPER.writeValueAsString(payload);
                if (json.length() <= MAX_CONTEXT_CHARS) {
                    return;
                }
            }
        } catch (IOException ignored) {
            // Cannot estimate size — proceed with the original payload
        }
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
                                                     String authToken,
                                                     List<Map<String, Object>> messages,
                                                     Map<String, Object> schema) {
        String content = callOllama(endpointUrl, modelName, authToken, messages, schema);
        try {
            return OBJECT_MAPPER.readValue(content, MAP_TYPE);
        } catch (IOException exception) {
            throw new BadRequestException("AI endpoint vratil nevalidni JSON odpoved: " + exception.getMessage());
        }
    }

    private String callTextOllama(String endpointUrl, String modelName, String authToken, List<Map<String, Object>> messages) {
        return callOllama(endpointUrl, modelName, authToken, messages, null);
    }

    private String callOllama(String endpointUrl,
                              String modelName,
                              String authToken,
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

        boolean isOllamaLegacy = isOllamaLegacyEndpoint(uri);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", messages);
        body.put("stream", false);

        if (isOllamaLegacy) {
            // Ollama native /api/chat format
            body.put("options", Map.of("temperature", 0.2));
            if (formatSchema != null) {
                body.put("format", formatSchema);
            }
        } else {
            // OpenAI-compatible format (/v1/chat/completions and compatible APIs)
            body.put("temperature", 0.2);
            if (formatSchema != null) {
                // Use structured outputs (json_schema) so the model is forced to
                // respect exact field names and types — prevents the model from
                // returning snake_case or renamed fields that break FE rendering.
                body.put("response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", "device_analysis",
                                "strict", true,
                                "schema", formatSchema
                        )
                ));
            }
        }

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                    .timeout(AI_ENDPOINT_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)));

            if (authToken != null && !authToken.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + authToken.trim());
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw new BadRequestException("AI endpoint vyzaduje autentizaci (401 Unauthorized). Zkontrolujte Bearer token v nastaveni AI.");
            }
            if (response.statusCode() >= 400) {
                throw new BadRequestException("AI endpoint vratil chybu " + response.statusCode() + ": " + extractErrorBody(response.body()));
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());

            // 1. OpenAI-compatible: choices[0].message.content
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            // 2. Ollama native /api/chat: message.content
            if (contentNode.isMissingNode() || contentNode.isNull() || contentNode.asText().isBlank()) {
                contentNode = root.path("message").path("content");
            }
            // 3. Ollama /api/generate fallback: response
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

    /**
     * Returns true for Ollama's native /api/chat endpoint that uses a different
     * request/response format than the OpenAI-compatible /v1/chat/completions.
     */
    private boolean isOllamaLegacyEndpoint(URI uri) {
        if (uri == null || uri.getPath() == null) {
            return false;
        }
        return uri.getPath().contains("/api/chat") || uri.getPath().contains("/api/generate");
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

                Pozadovana struktura JSON vystupu (pouzij presne tato jmena poli — zadne snake_case, zadne alternativni nazvy):
                {
                  "trafficLight": "GREEN" | "AMBER" | "RED",
                  "riskScore": <cislo 0–100>,
                  "confidence": "LOW" | "MEDIUM" | "HIGH",
                  "headline": "<jedna kratka veta>",
                  "summary": "<cesky, konkretni shrnutí>",
                  "overallAssessment": "<cesky, detailni hodnoceni>",
                  "timeline": [
                    { "time": "<HH:mm nebo kratky casovy udaj>", "title": "<nazev udalosti>", "severity": "INFO"|"LOW"|"MEDIUM"|"HIGH", "source": "<zdroj logu/dat>", "description": "<popis>", "reasoning": "<zduvodneni zavaznosti>" }
                  ],
                  "topRisks": [
                    { "title": "<nazev rizika>", "severity": "LOW"|"MEDIUM"|"HIGH", "whyItMatters": "<proc je to dulezite>", "relatedRuleCodes": ["<kod pravidla>"] }
                  ],
                  "recommendations": [
                    { "priority": "NOW"|"SOON"|"LATER", "action": "<konkretni krok>", "reason": "<zduvodneni>" }
                  ],
                  "followUpQuestions": ["<otazka>"]
                }

                Dulezite:
                - Dodrzuj presne nazvy poli vcetne velkeho/maleho pismene (camelCase).
                - Pokud jsou data klidna a bez zjevne anomalie, vrat GREEN a nevytvarej umele podezreni.
                - recommendations serad vzestupne podle priority: NOW, SOON, LATER.
                - followUpQuestions vypln jen pokud chybi dulezita data nebo je potreba dalsi kontrola.

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
