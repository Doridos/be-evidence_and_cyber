package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record RemoteSessionCreateRequest(
        UUID helpRequestId,
        UUID deviceId,
        @NotBlank String sessionType,
        @NotBlank String provider
) {
}
