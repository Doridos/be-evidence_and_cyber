package cz.fel.cvut.beevidence_and_cyber.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AIAnalysisRunDto(
        UUID id,
        UUID deviceId,
        UUID triggeredByUserId,
        String modelName,
        String promptVersion,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String resultSummary,
        BigDecimal riskScore,
        Map<String, Object> reportJson
) {
}
