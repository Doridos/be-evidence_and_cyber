package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record CommandRequestDto(
        UUID id,
        UUID deviceId,
        UUID requestedByUserId,
        String commandType,
        String status,
        Map<String, Object> payloadJson,
        LocalDateTime createdAt
) {
}
