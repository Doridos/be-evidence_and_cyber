package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AIAnalysisRunRequest(
        @NotNull UUID deviceId,
        @NotBlank String modelName,
        @NotBlank String promptVersion,
        String resultSummary
) {
}
