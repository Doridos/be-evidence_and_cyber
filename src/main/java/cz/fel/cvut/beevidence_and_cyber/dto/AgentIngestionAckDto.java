package cz.fel.cvut.beevidence_and_cyber.dto;

import java.util.UUID;

public record AgentIngestionAckDto(
        UUID deviceId,
        UUID snapshotId,
        UUID heartbeatId,
        UUID telemetryId,
        boolean snapshotUpdated
) {
}
