package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentDeploymentRequest(
        String targetHost,
        @NotBlank String username,
        @NotBlank String password
) {
}
