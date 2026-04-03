package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceCreateRequest(
        String assetTag,
        @NotBlank String hostname,
        String fqdn,
        String primaryIp,
        String site,
        boolean agentInstalled
) {
}
