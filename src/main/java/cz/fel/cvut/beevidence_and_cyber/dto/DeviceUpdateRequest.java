package cz.fel.cvut.beevidence_and_cyber.dto;

import java.util.UUID;

public record DeviceUpdateRequest(
        String assetTag,
        String inventoryNumber,
        String fqdn,
        String primaryIp,
        String site,
        UUID ownerId,
        Boolean agentInstalled
) {
}
