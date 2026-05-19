package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.config.LdapProperties;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.DeviceSubnetScanRequest;
import cz.fel.cvut.beevidence_and_cyber.exception.BadRequestException;
import cz.fel.cvut.beevidence_and_cyber.repository.EndpointDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class SubnetScanServiceTest {

    @Mock
    private EndpointDeviceRepository endpointDeviceRepository;
    @Mock
    private AuditService auditService;

    private SubnetScanService subnetScanService;

    @BeforeEach
    void setUp() {
        subnetScanService = new SubnetScanService(endpointDeviceRepository, auditService, new LdapProperties());
    }

    @Test
    public void givenSubnetWithTooManyHosts_whenScan_thenThrowBadRequestException() {
        User actor = new User();
        actor.setAdUsername("admin");

        assertThatThrownBy(() -> subnetScanService.scan(new DeviceSubnetScanRequest("192.168.1.0/24", 1000, 10), actor))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("překračuje povolený limit 10");
    }
}
