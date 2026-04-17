package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;

public record AIChatResponseDto(
        String answer,
        String modelName,
        String promptVersion,
        LocalDateTime generatedAt
) {
}
