package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ControlApprovalDto(
        UUID id,
        UUID remoteSessionId,
        LocalDateTime requestedAt,
        String decision,
        String decidedByUsername,
        LocalDateTime decidedAt,
        String note
) {
}
