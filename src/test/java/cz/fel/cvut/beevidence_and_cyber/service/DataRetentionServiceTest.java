package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.RetentionSettings;
import cz.fel.cvut.beevidence_and_cyber.dto.PurgeResultDto;
import cz.fel.cvut.beevidence_and_cyber.repository.AIAnalysisRunRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.AgentHeartbeatRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionFindingEventRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionFindingRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceLogEntryRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceSnapshotRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.FileSystemEventRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.LoggedInSessionRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.NetworkInterfaceRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.RemoteSessionRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.RetentionSettingsRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.TelemetrySampleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataRetentionServiceTest {

    @Mock
    private RetentionSettingsRepository retentionSettingsRepository;
    @Mock
    private DeviceLogEntryRepository deviceLogEntryRepository;
    @Mock
    private FileSystemEventRepository fileSystemEventRepository;
    @Mock
    private TelemetrySampleRepository telemetrySampleRepository;
    @Mock
    private AgentHeartbeatRepository agentHeartbeatRepository;
    @Mock
    private DeviceSnapshotRepository deviceSnapshotRepository;
    @Mock
    private NetworkInterfaceRepository networkInterfaceRepository;
    @Mock
    private LoggedInSessionRepository loggedInSessionRepository;
    @Mock
    private DetectionFindingRepository detectionFindingRepository;
    @Mock
    private DetectionFindingEventRepository detectionFindingEventRepository;
    @Mock
    private RemoteSessionRepository remoteSessionRepository;
    @Mock
    private AIAnalysisRunRepository aiAnalysisRunRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;

    private DataRetentionService dataRetentionService;

    @BeforeEach
    void setUp() {
        dataRetentionService = new DataRetentionService(
                retentionSettingsRepository,
                deviceLogEntryRepository,
                fileSystemEventRepository,
                telemetrySampleRepository,
                agentHeartbeatRepository,
                deviceSnapshotRepository,
                networkInterfaceRepository,
                loggedInSessionRepository,
                detectionFindingRepository,
                detectionFindingEventRepository,
                remoteSessionRepository,
                aiAnalysisRunRepository
        );
        ReflectionTestUtils.setField(dataRetentionService, "entityManager", entityManager);
    }

    @Test
    public void givenMissingRetentionSettings_whenGetSettings_thenPersistDefaults() {
        when(retentionSettingsRepository.findById(1)).thenReturn(Optional.empty());
        when(retentionSettingsRepository.save(any(RetentionSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RetentionSettings result = dataRetentionService.getSettings();

        assertThat(result.getId()).isEqualTo(1);
        assertThat(result.getRetentionDays()).isEqualTo(30);
        verify(retentionSettingsRepository).save(any(RetentionSettings.class));
    }

    @Test
    public void givenDatabaseWithinLimit_whenRunSizeCheck_thenReturnNoDeletionSummary() {
        RetentionSettings settings = new RetentionSettings();
        settings.setMaxDbSizeGb(1);
        when(retentionSettingsRepository.findById(1)).thenReturn(Optional.of(settings));
        when(entityManager.createNativeQuery("SELECT pg_database_size(current_database())")).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1024L);

        PurgeResultDto result = dataRetentionService.runSizeCheck();

        assertThat(result.success()).isTrue();
        assertThat(result.totalDeleted()).isZero();
        assertThat(result.message()).contains("DB je v limitu");
        verify(entityManager).createNativeQuery(eq("SELECT pg_database_size(current_database())"));
    }
}
