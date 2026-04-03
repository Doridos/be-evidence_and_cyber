package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record CommandRequestCreateRequest(
        @NotNull UUID deviceId,
        @NotBlank String commandType,
        @NotNull Map<String, Object> payloadJson
) {
}
