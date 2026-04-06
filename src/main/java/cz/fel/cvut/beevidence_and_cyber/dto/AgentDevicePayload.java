package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AgentDevicePayload(
        String hostname,
        String fqdn,
        String primaryIp,
        String osName,
        String osVersion,
        String osBuild,
        String osArchitecture,
        String domainName,
        String currentLoggedUser,
        OffsetDateTime lastBootAt,
        String agentVersion,
        OffsetDateTime collectedAt,
        List<AgentNetworkAdapterPayload> networkAdapters,
        List<AgentLoggedInSessionPayload> loggedInSessions
) {
}
