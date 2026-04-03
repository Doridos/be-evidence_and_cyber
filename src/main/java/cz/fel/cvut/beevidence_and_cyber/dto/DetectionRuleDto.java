package cz.fel.cvut.beevidence_and_cyber.dto;

import java.util.Map;
import java.util.UUID;

public record DetectionRuleDto(
        UUID id,
        String code,
        String name,
        String description,
        String severity,
        String sourceType,
        Map<String, Object> conditionJson,
        boolean enabled
) {
}
