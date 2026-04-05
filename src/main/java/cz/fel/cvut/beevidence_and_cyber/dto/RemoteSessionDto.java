package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RemoteSessionDto(
        UUID id,
        UUID helpRequestId,
        UUID deviceId,
        String deviceHostname,
        String primaryIp,
        String remoteAssistanceTarget,
        String remoteAssistanceUri,
        UUID adminUserId,
        String sessionType,
        String provider,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
}
