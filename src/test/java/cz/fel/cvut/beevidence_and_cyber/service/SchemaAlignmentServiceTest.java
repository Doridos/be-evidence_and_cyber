package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.DeviceOwner;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceDepartmentRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceOwnerRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.EndpointDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaAlignmentServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DeviceDepartmentRepository deviceDepartmentRepository;
    @Mock
    private DeviceOwnerRepository deviceOwnerRepository;
    @Mock
    private EndpointDeviceRepository endpointDeviceRepository;

    private SchemaAlignmentService schemaAlignmentService;

    @BeforeEach
    void setUp() {
        schemaAlignmentService = new SchemaAlignmentService(jdbcTemplate, deviceDepartmentRepository, deviceOwnerRepository, endpointDeviceRepository);
    }

    @Test
    public void givenLegacyDeviceOwnerFields_whenRun_thenMigrateOwnersToRelation() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname("pc-01");
        device.setOwnerFirstName(" Alice ");
        device.setOwnerLastName(" Smith ");
        when(deviceOwnerRepository.findAll()).thenReturn(List.of());
        when(endpointDeviceRepository.findAll()).thenReturn(List.of(device));
        when(deviceOwnerRepository.save(any(DeviceOwner.class))).thenAnswer(invocation -> {
            DeviceOwner owner = invocation.getArgument(0);
            owner.setId(UUID.randomUUID());
            return owner;
        });
        when(endpointDeviceRepository.save(any(EndpointDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        schemaAlignmentService.run();

        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).execute(any(String.class));
        ArgumentCaptor<DeviceOwner> ownerCaptor = ArgumentCaptor.forClass(DeviceOwner.class);
        verify(deviceOwnerRepository).save(ownerCaptor.capture());
        assertThat(ownerCaptor.getValue().getFirstName()).isEqualTo("Alice");
        assertThat(ownerCaptor.getValue().getLastName()).isEqualTo("Smith");
        assertThat(device.getOwner()).isNotNull();
    }
}
