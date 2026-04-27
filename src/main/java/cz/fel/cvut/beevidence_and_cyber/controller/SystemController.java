package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dao.RetentionSettings;
import cz.fel.cvut.beevidence_and_cyber.dto.PurgeResultDto;
import cz.fel.cvut.beevidence_and_cyber.dto.RetentionSettingsDto;
import cz.fel.cvut.beevidence_and_cyber.dto.SystemCapacityDto;
import cz.fel.cvut.beevidence_and_cyber.service.DataRetentionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    private final DataRetentionService dataRetentionService;

    /** Returns current DB size, per-table breakdown and retention settings. */
    @GetMapping("/capacity")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemCapacityDto> getCapacity() {
        return ResponseEntity.ok(dataRetentionService.getSystemCapacity());
    }

    /** Returns current retention settings. */
    @GetMapping("/retention/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RetentionSettings> getSettings() {
        return ResponseEntity.ok(dataRetentionService.getSettings());
    }

    /** Updates retention settings (retentionDays, maxDbSizeGb, retentionEnabled). */
    @PutMapping("/retention/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RetentionSettings> updateSettings(@Valid @RequestBody RetentionSettingsDto dto) {
        return ResponseEntity.ok(dataRetentionService.updateSettings(dto));
    }

    /**
     * Manually triggers a full retention purge (time-based + size check).
     * Returns a summary of deleted record counts per table.
     */
    @PostMapping("/retention/purge")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PurgeResultDto> runPurge() {
        return ResponseEntity.ok(dataRetentionService.runPurge());
    }
}
