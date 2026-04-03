package cz.fel.cvut.beevidence_and_cyber.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TelemetrySampleDto(
        UUID id,
        LocalDateTime collectedAt,
        BigDecimal cpuUsagePct,
        BigDecimal memoryUsagePct,
        BigDecimal diskUsagePct,
        Long processCount,
        Long serviceCount
) {
}
