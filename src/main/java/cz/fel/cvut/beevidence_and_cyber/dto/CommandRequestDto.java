package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record CommandRequestDto(
        UUID id,
        UUID deviceId,
        String deviceHostname,
        UUID requestedByUserId,
        String requestedByUsername,
        String commandType,
        String status,
        Map<String, Object> payloadJson,
        LocalDateTime createdAt,
        CommandExecutionDto latestExecution
) {
}
