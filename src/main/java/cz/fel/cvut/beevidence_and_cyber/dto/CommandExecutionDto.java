package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CommandExecutionDto(
        UUID id,
        UUID commandRequestId,
        UUID agentHeartbeatId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer exitCode,
        String resultSummary,
        String errorMessage
) {
}
