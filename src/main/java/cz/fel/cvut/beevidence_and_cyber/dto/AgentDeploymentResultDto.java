package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AgentDeploymentResultDto(
        UUID deviceId,
        String deviceHostname,
        String targetHost,
        boolean success,
        String status,
        String message,
        String deploymentSourceDir,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long durationMs
) {
}
