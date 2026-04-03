package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentHelpRequestInput(
        UUID requestId,
        @NotBlank String requestedByUsername,
        String requestedByDisplayName,
        String requestedByDomain,
        @NotBlank String deviceHostname,
        String deviceFqdn,
        String primaryIp,
        String message,
        OffsetDateTime requestedAt
) {
}
