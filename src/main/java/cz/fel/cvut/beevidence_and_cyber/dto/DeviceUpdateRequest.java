package cz.fel.cvut.beevidence_and_cyber.dto;

public record DeviceUpdateRequest(
        String assetTag,
        String fqdn,
        String primaryIp,
        String site,
        Boolean agentInstalled
) {
}
