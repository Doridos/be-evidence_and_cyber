package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DetectionFindingDto(
        UUID id,
        UUID deviceId,
        String deviceHostname,
        UUID ruleId,
        String ruleCode,
        String status,
        String severity,
        String title,
        String description,
        LocalDateTime firstSeenAt,
        LocalDateTime lastSeenAt,
        boolean createdByAi,
        Map<String, Object> contextJson,
        List<DetectionFindingEventDto> events
) {
}
