package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record PurgeResultDto(
        boolean success,
        LocalDateTime purgedAt,
        Map<String, Long> deletedCounts,
        long totalDeleted,
        String message
) {
}
