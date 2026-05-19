package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.RetentionSettings;
import cz.fel.cvut.beevidence_and_cyber.dto.PurgeResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionSchedulerTest {

    @Mock
    private DataRetentionService dataRetentionService;

    private RetentionScheduler retentionScheduler;

    @BeforeEach
    void setUp() {
        retentionScheduler = new RetentionScheduler(dataRetentionService);
    }

    @Test
    public void givenRetentionDisabled_whenScheduledRetentionPurge_thenSkipPurgeExecution() {
        RetentionSettings settings = new RetentionSettings();
        settings.setRetentionEnabled(false);
        when(dataRetentionService.getSettings()).thenReturn(settings);

        retentionScheduler.scheduledRetentionPurge();

        verify(dataRetentionService, never()).runPurge();
    }

    @Test
    public void givenRetentionEnabledAndPurgeFails_whenScheduledRetentionPurge_thenSwallowException() {
        RetentionSettings settings = new RetentionSettings();
        settings.setRetentionEnabled(true);
        when(dataRetentionService.getSettings()).thenReturn(settings);
        doThrow(new IllegalStateException("failure")).when(dataRetentionService).runPurge();

        retentionScheduler.scheduledRetentionPurge();

        verify(dataRetentionService).runPurge();
    }

    @Test
    public void givenRetentionEnabled_whenScheduledRetentionPurge_thenRunPurge() {
        RetentionSettings settings = new RetentionSettings();
        settings.setRetentionEnabled(true);
        when(dataRetentionService.getSettings()).thenReturn(settings);
        when(dataRetentionService.runPurge()).thenReturn(
                new PurgeResultDto(true, LocalDateTime.now(), Map.of("logs", 3L), 3L, "done")
        );

        retentionScheduler.scheduledRetentionPurge();

        verify(dataRetentionService).runPurge();
    }
}
