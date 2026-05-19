package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.DeviceOwner;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.DeviceOwnerCreateRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.DeviceOwnerOptionDto;
import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.DeviceStatusEnum;
import cz.fel.cvut.beevidence_and_cyber.repository.AgentHeartbeatRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceDepartmentRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceLogEntryRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceOwnerRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceSnapshotRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.EndpointDeviceRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.FileSystemEventRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.LoggedInSessionRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.NetworkInterfaceRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.TelemetrySampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private EndpointDeviceRepository endpointDeviceRepository;
    @Mock
    private DeviceDepartmentRepository deviceDepartmentRepository;
    @Mock
    private DeviceOwnerRepository deviceOwnerRepository;
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
    private DeviceLogEntryRepository deviceLogEntryRepository;
    @Mock
    private FileSystemEventRepository fileSystemEventRepository;
    @Mock
    private SubnetScanService subnetScanService;
    @Mock
    private AgentDeploymentService agentDeploymentService;
    @Mock
    private AuditService auditService;

    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(
                endpointDeviceRepository,
                deviceDepartmentRepository,
                deviceOwnerRepository,
                deviceSnapshotRepository,
                networkInterfaceRepository,
                loggedInSessionRepository,
                agentHeartbeatRepository,
                telemetrySampleRepository,
                deviceLogEntryRepository,
                fileSystemEventRepository,
                subnetScanService,
                agentDeploymentService,
                new ApiMapper(),
                auditService
        );
    }

    @Test
    public void givenNewOwnerNamesWithWhitespace_whenCreateOwner_thenReturnNormalizedOwnerAndPersistIt() {
        User actor = createUser("admin");
        DeviceOwner savedOwner = new DeviceOwner();
        savedOwner.setId(UUID.randomUUID());
        savedOwner.setFirstName("Alice");
        savedOwner.setLastName("Smith");
        when(deviceOwnerRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCase("Alice", "Smith")).thenReturn(Optional.empty());
        when(deviceOwnerRepository.save(any(DeviceOwner.class))).thenReturn(savedOwner);

        DeviceOwnerOptionDto result = deviceService.createOwner(new DeviceOwnerCreateRequest("  Alice  ", "  Smith "), actor);

        assertThat(result.firstName()).isEqualTo("Alice");
        assertThat(result.lastName()).isEqualTo("Smith");
        assertThat(result.displayName()).isEqualTo("Alice Smith");

        ArgumentCaptor<DeviceOwner> ownerCaptor = ArgumentCaptor.forClass(DeviceOwner.class);
        verify(deviceOwnerRepository).save(ownerCaptor.capture());
        assertThat(ownerCaptor.getValue().getFirstName()).isEqualTo("Alice");
        assertThat(ownerCaptor.getValue().getLastName()).isEqualTo("Smith");
        verify(auditService).log(eq(actor), eq(ActorSourceEnum.WEB), eq("CREATE_DEVICE_OWNER"), eq("DEVICE_OWNER"),
                eq(savedOwner.getId()), eq(AuditResultEnum.SUCCESS), eq(Map.of("firstName", "Alice", "lastName", "Smith")));
    }

    @Test
    public void givenBlankOwnerFirstName_whenCreateOwner_thenThrowBadRequestException() {
        User actor = createUser("admin");

        assertThatThrownBy(() -> deviceService.createOwner(new DeviceOwnerCreateRequest("   ", "Smith"), actor))
                .isInstanceOf(cz.fel.cvut.beevidence_and_cyber.exception.BadRequestException.class)
                .hasMessage("Vlastník musí obsahovat jméno i příjmení.");

        verify(deviceOwnerRepository, never()).save(any(DeviceOwner.class));
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void givenInstalledAgentWithStaleHeartbeat_whenToSummaryDto_thenReturnUnreachableStatus() {
        EndpointDevice device = createDevice(true, DeviceStatusEnum.ACTIVE);
        when(deviceSnapshotRepository.findTopByDeviceOrderByVersionNoDesc(device)).thenReturn(Optional.empty());
        when(agentHeartbeatRepository.findByDeviceOrderByLastSeenAtDesc(device)).thenReturn(List.of(
                new cz.fel.cvut.beevidence_and_cyber.dao.AgentHeartbeat() {{
                    setId(UUID.randomUUID());
                    setLastSeenAt(LocalDateTime.now().minusMinutes(10));
                }}
        ));

        String status = deviceService.toSummaryDto(device).status();

        assertThat(status).isEqualTo(DeviceStatusEnum.UNREACHABLE.name());
    }

    @Test
    public void givenInstalledAgentWithRecentHeartbeat_whenToSummaryDto_thenReturnActiveStatus() {
        EndpointDevice device = createDevice(true, DeviceStatusEnum.ACTIVE);
        when(deviceSnapshotRepository.findTopByDeviceOrderByVersionNoDesc(device)).thenReturn(Optional.empty());
        when(agentHeartbeatRepository.findByDeviceOrderByLastSeenAtDesc(device)).thenReturn(List.of(
                new cz.fel.cvut.beevidence_and_cyber.dao.AgentHeartbeat() {{
                    setId(UUID.randomUUID());
                    setLastSeenAt(LocalDateTime.now().minusMinutes(1));
                }}
        ));

        String status = deviceService.toSummaryDto(device).status();

        assertThat(status).isEqualTo(DeviceStatusEnum.ACTIVE.name());
    }

    private EndpointDevice createDevice(boolean agentInstalled, DeviceStatusEnum status) {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname("pc-01");
        device.setStatus(status);
        device.setAgentInstalled(agentInstalled);
        return device;
    }

    private User createUser(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAdUsername(username);
        user.setDisplayName(username);
        return user;
    }
}
