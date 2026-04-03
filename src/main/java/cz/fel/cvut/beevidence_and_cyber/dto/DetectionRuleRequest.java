package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record DetectionRuleRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @NotBlank String severity,
        @NotBlank String sourceType,
        @NotNull Map<String, Object> conditionJson,
        boolean enabled
) {
}
