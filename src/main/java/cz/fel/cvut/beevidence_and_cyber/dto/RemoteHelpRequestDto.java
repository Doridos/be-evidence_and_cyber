package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RemoteHelpRequestDto(
        UUID id,
        UUID deviceId,
        String deviceHostname,
        String deviceFqdn,
        String primaryIp,
        String remoteAssistanceTarget,
        String remoteAssistanceUri,
        String requestedByUsername,
        String requestedByDisplayName,
        String message,
        String status,
        LocalDateTime requestedAt,
        UUID acceptedByUserId,
        LocalDateTime acceptedAt
) {
}
