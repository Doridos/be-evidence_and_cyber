package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RemoteSessionCreateRequest(
        @NotNull UUID helpRequestId,
        @NotBlank String sessionType,
        @NotBlank String provider
) {
}
