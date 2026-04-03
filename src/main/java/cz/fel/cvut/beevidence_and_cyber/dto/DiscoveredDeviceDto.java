package cz.fel.cvut.beevidence_and_cyber.dto;

import java.util.UUID;

public record DiscoveredDeviceDto(
        String ipAddress,
        String hostname,
        String fqdn,
        Long responseTimeMs,
        boolean alreadyInInventory,
        UUID existingDeviceId,
        String existingDeviceHostname,
        boolean agentInstalled,
        String suggestedHostname
) {
}
