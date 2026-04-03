package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AgentHeartbeatDto(
        UUID id,
        String agentVersion,
        String serviceStatus,
        LocalDateTime lastSeenAt,
        LocalDateTime lastCollectAt,
        String lastError
) {
}
