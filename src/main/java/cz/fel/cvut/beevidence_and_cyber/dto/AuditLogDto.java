package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditLogDto(
        UUID id,
        UUID actorUserId,
        String actorSource,
        String actionType,
        String targetType,
        UUID targetId,
        String result,
        LocalDateTime createdAt,
        Map<String, Object> detailsJson
) {
}
