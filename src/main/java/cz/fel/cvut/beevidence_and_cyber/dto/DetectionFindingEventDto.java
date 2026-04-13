package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record DetectionFindingEventDto(
        UUID id,
        String eventType,
        LocalDateTime occurredAt,
        UUID sourceRecordId,
        String sourceLog,
        String level,
        String eventCode,
        String message,
        String path,
        String actorUsername,
        Map<String, Object> payloadJson
) {
}
