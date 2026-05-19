package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dto.AIAnalysisRunRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.AIChatRequest;
import cz.fel.cvut.beevidence_and_cyber.exception.BadRequestException;
import cz.fel.cvut.beevidence_and_cyber.repository.AIAnalysisRunRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.AgentHeartbeatRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.CommandExecutionRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.CommandRequestRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionFindingEventRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionFindingRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceLogEntryRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceSnapshotRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.FileSystemEventRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.LoggedInSessionRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.NetworkInterfaceRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.RemoteSessionRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.TelemetrySampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

    @Mock
    private AIAnalysisRunRepository aiAnalysisRunRepository;
    @Mock
    private DetectionFindingRepository detectionFindingRepository;
    @Mock
    private DetectionFindingEventRepository detectionFindingEventRepository;
    @Mock
    private DeviceLogEntryRepository deviceLogEntryRepository;
    @Mock
    private FileSystemEventRepository fileSystemEventRepository;
    @Mock
    private DeviceSnapshotRepository deviceSnapshotRepository;
    @Mock
    private NetworkInterfaceRepository networkInterfaceRepository;
    @Mock
    private LoggedInSessionRepository loggedInSessionRepository;
    @Mock
    private TelemetrySampleRepository telemetrySampleRepository;
    @Mock
    private AgentHeartbeatRepository agentHeartbeatRepository;
    @Mock
    private RemoteSessionRepository remoteSessionRepository;
    @Mock
    private CommandRequestRepository commandRequestRepository;
    @Mock
    private CommandExecutionRepository commandExecutionRepository;
    @Mock
    private DeviceService deviceService;
    @Mock
    private AuditService auditService;

    private AiAnalysisService aiAnalysisService;

    @BeforeEach
    void setUp() {
        aiAnalysisService = new AiAnalysisService(
                aiAnalysisRunRepository,
                detectionFindingRepository,
                detectionFindingEventRepository,
                deviceLogEntryRepository,
                fileSystemEventRepository,
                deviceSnapshotRepository,
                networkInterfaceRepository,
                loggedInSessionRepository,
                telemetrySampleRepository,
                agentHeartbeatRepository,
                remoteSessionRepository,
                commandRequestRepository,
                commandExecutionRepository,
                deviceService,
                new ApiMapper(),
                auditService
        );
    }

    @Test
    public void givenInvertedWindow_whenCreateAiRun_thenThrowBadRequestException() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        when(deviceService.findDevice(device.getId())).thenReturn(device);

        assertThatThrownBy(() -> aiAnalysisService.createAiRun(new AIAnalysisRunRequest(
                device.getId(),
                "http://localhost:11434",
                "llama3",
                LocalDateTime.now(),
                LocalDateTime.now().minusDays(1),
                null,
                null
        ), new cz.fel.cvut.beevidence_and_cyber.dao.User()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Počátek období musí být dříve než jeho konec.");
    }

    @Test
    public void givenAnalysisWindowLongerThan31Days_whenChat_thenThrowBadRequestException() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        when(deviceService.findDevice(device.getId())).thenReturn(device);

        assertThatThrownBy(() -> aiAnalysisService.chat(device.getId(), new AIChatRequest(
                "http://localhost:11434",
                "llama3",
                LocalDateTime.now().minusDays(40),
                LocalDateTime.now(),
                "question",
                List.of(),
                null
        ), new cz.fel.cvut.beevidence_and_cyber.dao.User()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("AI analýza aktuálně podporuje maximálně 31 dní v jednom běhu.");
    }
}
