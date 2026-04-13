package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record DeviceCreateRequest(
        String assetTag,
        @NotBlank String hostname,
        String fqdn,
        String primaryIp,
        String site,
        UUID ownerId,
        boolean agentInstalled
) {
}
