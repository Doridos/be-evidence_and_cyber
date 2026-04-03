package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AgentHeartbeatRequest(
        @Valid @NotNull AgentDevicePayload device,
        @Valid @NotNull AgentTelemetryPayload telemetry
) {
}
