package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.RetentionSettings;
import cz.fel.cvut.beevidence_and_cyber.dto.PurgeResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionScheduler {

    private final DataRetentionService dataRetentionService;

    /**
     * Runs the full data retention purge every night at 02:15.
     * The job is skipped automatically when retentionEnabled = false.
     */
    @Scheduled(cron = "0 15 2 * * *")
    public void scheduledRetentionPurge() {
        RetentionSettings settings = dataRetentionService.getSettings();
        if (!settings.isRetentionEnabled()) {
            log.debug("Retention purge skipped — retentionEnabled is false");
            return;
        }
        log.info("Starting scheduled retention purge (retentionDays={}, maxDbSizeGb={})",
                settings.getRetentionDays(), settings.getMaxDbSizeGb());
        try {
            PurgeResultDto result = dataRetentionService.runPurge();
            log.info("Scheduled retention purge finished — deleted {} records", result.totalDeleted());
        } catch (Exception ex) {
            log.error("Scheduled retention purge failed: {}", ex.getMessage(), ex);
        }
    }
}
