package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.config.AgentAccessProperties;
import cz.fel.cvut.beevidence_and_cyber.config.AgentDeploymentProperties;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.AgentDeploymentRequest;
import cz.fel.cvut.beevidence_and_cyber.exception.BadRequestException;
import cz.fel.cvut.beevidence_and_cyber.repository.EndpointDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AgentDeploymentServiceTest {

    @Mock
    private AgentDeploymentPackageService packageService;
    @Mock
    private AuditService auditService;
    @Mock
    private EndpointDeviceRepository endpointDeviceRepository;

    private AgentDeploymentProperties deploymentProperties;
    private AgentDeploymentService agentDeploymentService;

    @BeforeEach
    void setUp() {
        deploymentProperties = new AgentDeploymentProperties();
        deploymentProperties.setEnabled(false);
        agentDeploymentService = new AgentDeploymentService(
                deploymentProperties,
                new AgentAccessProperties(),
                packageService,
                auditService,
                endpointDeviceRepository
        );
    }

    @Test
    public void givenDeploymentDisabled_whenDeployAgent_thenThrowBadRequestException() {
        EndpointDevice device = new EndpointDevice();
        device.setHostname("pc-01");
        User actor = new User();

        assertThatThrownBy(() -> agentDeploymentService.deployAgent(
                device,
                new AgentDeploymentRequest(null, "admin", "secret"),
                actor
        )).isInstanceOf(BadRequestException.class)
                .hasMessage("Vzdálená instalace agenta je v konfiguraci backendu vypnutá.");
    }
}
