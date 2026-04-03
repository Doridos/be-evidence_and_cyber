package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.OffsetDateTime;

public record AgentLogEntryPayload(
        OffsetDateTime occurredAt,
        String logSource,
        String level,
        String eventCode,
        String message,
        String rawPayload
) {
}
