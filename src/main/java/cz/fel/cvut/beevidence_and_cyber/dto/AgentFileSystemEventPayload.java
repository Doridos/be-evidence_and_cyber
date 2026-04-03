package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record AgentFileSystemEventPayload(
        OffsetDateTime occurredAt,
        String eventType,
        String path,
        String actorUsername,
        String sourceLog,
        Map<String, Object> detailsJson
) {
}
