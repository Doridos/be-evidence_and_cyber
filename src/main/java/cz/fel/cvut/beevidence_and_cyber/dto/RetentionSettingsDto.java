package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RetentionSettingsDto(
        @Min(1) @Max(3650) int retentionDays,
        @Min(1) @Max(100_000) int maxDbSizeGb,
        boolean retentionEnabled
) {
}
