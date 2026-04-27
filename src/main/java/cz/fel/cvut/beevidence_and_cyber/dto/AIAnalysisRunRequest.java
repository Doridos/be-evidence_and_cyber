package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record AIAnalysisRunRequest(
        @NotNull UUID deviceId,
        @NotBlank String endpointUrl,
        @NotBlank String modelName,
        @NotNull LocalDateTime from,
        @NotNull LocalDateTime to,
        String analystQuestion,
        /** Optional Bearer token for AI endpoints that require authentication. */
        String authToken
) {
}
