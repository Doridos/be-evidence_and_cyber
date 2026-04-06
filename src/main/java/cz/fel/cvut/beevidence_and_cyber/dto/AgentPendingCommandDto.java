package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AgentPendingCommandDto(
        UUID id,
        String commandType,
        Map<String, Object> payloadJson,
        LocalDateTime createdAt
) {
}
