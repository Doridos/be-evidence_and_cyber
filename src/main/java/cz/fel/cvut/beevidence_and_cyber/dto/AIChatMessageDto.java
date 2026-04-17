package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotBlank;

public record AIChatMessageDto(
        @NotBlank String role,
        @NotBlank String content
) {
}
