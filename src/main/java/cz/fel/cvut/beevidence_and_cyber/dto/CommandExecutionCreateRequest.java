package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record CommandExecutionCreateRequest(
        @NotNull UUID commandRequestId,
        UUID agentHeartbeatId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer exitCode,
        String resultSummary,
        String errorMessage
) {
}
