package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SystemCapacityDto(
        long dbSizeBytes,
        long maxDbSizeBytes,
        double usagePct,
        List<TableSizeEntryDto> tables,
        int retentionDays,
        int maxDbSizeGb,
        boolean retentionEnabled,
        LocalDateTime lastPurgedAt
) {
}
