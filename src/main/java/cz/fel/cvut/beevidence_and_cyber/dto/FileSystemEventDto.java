package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record FileSystemEventDto(
        UUID id,
        LocalDateTime occurredAt,
        String eventType,
        String path,
        String actorUsername,
        String sourceLog,
        Map<String, Object> detailsJson
) {
}
