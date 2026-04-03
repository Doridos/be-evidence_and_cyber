package cz.fel.cvut.beevidence_and_cyber.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AgentTelemetryPayload(
        OffsetDateTime collectedAt,
        BigDecimal cpuUsagePct,
        BigDecimal memoryUsagePct,
        BigDecimal diskUsagePct,
        Long processCount,
        Long serviceCount
) {
}
