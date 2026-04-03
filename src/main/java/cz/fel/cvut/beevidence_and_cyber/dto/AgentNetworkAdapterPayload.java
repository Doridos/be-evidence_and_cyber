package cz.fel.cvut.beevidence_and_cyber.dto;

import java.util.List;

public record AgentNetworkAdapterPayload(
        String name,
        String displayName,
        String macAddress,
        List<String> ipv4Addresses,
        List<String> ipv6Addresses,
        boolean primary,
        boolean up
) {
}
