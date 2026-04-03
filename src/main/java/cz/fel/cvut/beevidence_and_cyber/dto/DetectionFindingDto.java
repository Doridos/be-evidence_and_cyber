package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DetectionFindingDto(
        UUID id,
        UUID deviceId,
        UUID ruleId,
        String status,
        String severity,
        String title,
        String description,
        LocalDateTime firstSeenAt,
        LocalDateTime lastSeenAt,
        boolean createdByAi
) {
}
