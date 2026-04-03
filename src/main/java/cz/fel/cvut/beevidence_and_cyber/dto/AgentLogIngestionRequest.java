package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AgentLogIngestionRequest(
        @NotBlank String deviceHostname,
        List<AgentLogEntryPayload> logEntries,
        List<AgentFileSystemEventPayload> fileSystemEvents
) {
}
