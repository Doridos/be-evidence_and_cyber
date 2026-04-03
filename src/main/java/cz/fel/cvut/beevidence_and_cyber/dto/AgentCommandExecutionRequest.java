package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentCommandExecutionRequest(
        @NotNull UUID commandRequestId,
        UUID agentHeartbeatId,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Integer exitCode,
        String resultSummary,
        String errorMessage
) {
}
