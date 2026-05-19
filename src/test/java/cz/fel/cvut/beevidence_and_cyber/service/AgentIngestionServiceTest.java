package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.AgentHeartbeat;
import cz.fel.cvut.beevidence_and_cyber.dao.CommandExecution;
import cz.fel.cvut.beevidence_and_cyber.dao.CommandRequest;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dto.AgentCommandExecutionRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.AgentHelpRequestInput;
import cz.fel.cvut.beevidence_and_cyber.dto.CommandExecutionDto;
import cz.fel.cvut.beevidence_and_cyber.dto.RemoteHelpRequestDto;
import cz.fel.cvut.beevidence_and_cyber.enumeration.CommandStatusEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.CommandTypeEnum;
import cz.fel.cvut.beevidence_and_cyber.repository.AgentHeartbeatRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.CommandExecutionRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.CommandRequestRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceLogEntryRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceSnapshotRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.EndpointDeviceRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.FileSystemEventRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.LoggedInSessionRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.NetworkInterfaceRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.RemoteHelpRequestRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.TelemetrySampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cache.CacheManager;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentIngestionServiceTest {

    @Mock
    private EndpointDeviceRepository endpointDeviceRepository;
    @Mock
    private DeviceSnapshotRepository deviceSnapshotRepository;
    @Mock
    private NetworkInterfaceRepository networkInterfaceRepository;
    @Mock
    private LoggedInSessionRepository loggedInSessionRepository;
    @Mock
    private AgentHeartbeatRepository agentHeartbeatRepository;
    @Mock
    private TelemetrySampleRepository telemetrySampleRepository;
    @Mock
    private RemoteHelpRequestRepository remoteHelpRequestRepository;
    @Mock
    private DeviceLogEntryRepository deviceLogEntryRepository;
    @Mock
    private FileSystemEventRepository fileSystemEventRepository;
    @Mock
    private CommandRequestRepository commandRequestRepository;
    @Mock
    private CommandExecutionRepository commandExecutionRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private DetectionService detectionService;
    @Mock
    private CacheManager cacheManager;

    private AgentIngestionService agentIngestionService;

    @BeforeEach
    void setUp() {
        agentIngestionService = new AgentIngestionService(
                endpointDeviceRepository,
                deviceSnapshotRepository,
                networkInterfaceRepository,
                loggedInSessionRepository,
                agentHeartbeatRepository,
                telemetrySampleRepository,
                remoteHelpRequestRepository,
                deviceLogEntryRepository,
                fileSystemEventRepository,
                commandRequestRepository,
                commandExecutionRepository,
                auditService,
                new ApiMapper(),
                detectionService,
                cacheManager
        );
    }

    @Test
    public void givenUnknownDevice_whenIngestHelpRequest_thenCreateDeviceAndReturnHelpRequestDto() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname("pc-01");
        when(endpointDeviceRepository.findByHostnameIgnoreCase("pc-01")).thenReturn(Optional.empty());
        when(endpointDeviceRepository.save(any(EndpointDevice.class))).thenReturn(device);
        when(remoteHelpRequestRepository.save(any())).thenAnswer(invocation -> {
            cz.fel.cvut.beevidence_and_cyber.dao.RemoteHelpRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        RemoteHelpRequestDto result = agentIngestionService.ingestHelpRequest(new AgentHelpRequestInput(
                null, "alice", "Alice", null, "pc-01", null, "10.0.0.5", "Need help", OffsetDateTime.now()
        ));

        assertThat(result.deviceHostname()).isEqualTo("pc-01");
        assertThat(result.requestedByUsername()).isEqualTo("alice");
    }

    @Test
    public void givenUsbCommandExecutionWithBlockMode_whenIngestCommandExecution_thenUpdateDeviceUsbBlockState() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname("pc-01");
        CommandRequest commandRequest = new CommandRequest();
        commandRequest.setId(UUID.randomUUID());
        commandRequest.setDevice(device);
        commandRequest.setCommandType(CommandTypeEnum.USB);
        commandRequest.setStatus(CommandStatusEnum.RUNNING);
        when(commandRequestRepository.findById(commandRequest.getId())).thenReturn(Optional.of(commandRequest));
        when(commandExecutionRepository.save(any(CommandExecution.class))).thenAnswer(invocation -> {
            CommandExecution execution = invocation.getArgument(0);
            execution.setId(UUID.randomUUID());
            return execution;
        });

        CommandExecutionDto result = agentIngestionService.ingestCommandExecution(new AgentCommandExecutionRequest(
                commandRequest.getId(),
                null,
                OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now(),
                0,
                "done",
                null,
                Map.of("mode", "BLOCK")
        ));

        assertThat(result.exitCode()).isZero();
        assertThat(device.isUsbRemovableBlocked()).isTrue();
        assertThat(commandRequest.getStatus()).isEqualTo(CommandStatusEnum.SUCCESS);
        verify(endpointDeviceRepository).save(device);
    }
}
