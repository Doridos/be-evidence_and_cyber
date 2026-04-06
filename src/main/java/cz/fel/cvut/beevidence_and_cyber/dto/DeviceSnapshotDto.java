package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DeviceSnapshotDto(
        UUID id,
        Integer versionNo,
        LocalDateTime collectedAt,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        String hostname,
        String osName,
        String osVersion,
        String osBuild,
        String osArchitecture,
        String domainName,
        String currentLoggedUser,
        LocalDateTime lastBootAt,
        String javaAgentVersion,
        List<NetworkInterfaceDto> networkInterfaces,
        List<LoggedInSessionDto> loggedInSessions
) {
}
