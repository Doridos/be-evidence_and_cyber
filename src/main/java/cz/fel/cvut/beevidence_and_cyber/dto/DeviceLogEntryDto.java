package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DeviceLogEntryDto(
        UUID id,
        LocalDateTime occurredAt,
        String logSource,
        String level,
        String eventCode,
        String message,
        String rawPayload
) {
}
