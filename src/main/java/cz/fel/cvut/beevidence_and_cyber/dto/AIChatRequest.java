package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record AIChatRequest(
        @NotBlank String endpointUrl,
        @NotBlank String modelName,
        @NotNull LocalDateTime from,
        @NotNull LocalDateTime to,
        @NotBlank String question,
        List<@Valid AIChatMessageDto> history
) {
}
