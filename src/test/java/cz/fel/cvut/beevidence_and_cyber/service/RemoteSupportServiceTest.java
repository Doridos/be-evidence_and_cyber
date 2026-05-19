package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dao.RemoteHelpRequest;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.RemoteHelpRequestDto;
import cz.fel.cvut.beevidence_and_cyber.dto.RemoteSessionCreateRequest;
import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.HelpRequestStatusEnum;
import cz.fel.cvut.beevidence_and_cyber.exception.BadRequestException;
import cz.fel.cvut.beevidence_and_cyber.repository.ControlApprovalRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.EndpointDeviceRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.RemoteHelpRequestRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.RemoteSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteSupportServiceTest {

    @Mock
    private RemoteHelpRequestRepository remoteHelpRequestRepository;
    @Mock
    private RemoteSessionRepository remoteSessionRepository;
    @Mock
    private ControlApprovalRepository controlApprovalRepository;
    @Mock
    private EndpointDeviceRepository endpointDeviceRepository;
    @Mock
    private AuditService auditService;

    private RemoteSupportService remoteSupportService;

    @BeforeEach
    void setUp() {
        remoteSupportService = new RemoteSupportService(
                remoteHelpRequestRepository,
                remoteSessionRepository,
                controlApprovalRepository,
                endpointDeviceRepository,
                new ApiMapper(),
                auditService
        );
    }

    @Test
    public void givenNewHelpRequest_whenAcceptHelpRequest_thenReturnAcceptedRequest() {
        User actor = createUser("admin");
        EndpointDevice device = createDevice("pc-01");
        RemoteHelpRequest helpRequest = new RemoteHelpRequest();
        helpRequest.setId(UUID.randomUUID());
        helpRequest.setDevice(device);
        helpRequest.setRequestedByUsername("alice");
        helpRequest.setStatus(HelpRequestStatusEnum.NEW);
        when(remoteHelpRequestRepository.findById(helpRequest.getId())).thenReturn(Optional.of(helpRequest));
        when(remoteHelpRequestRepository.save(any(RemoteHelpRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RemoteHelpRequestDto result = remoteSupportService.acceptHelpRequest(helpRequest.getId(), actor);

        assertThat(result.status()).isEqualTo(HelpRequestStatusEnum.ACCEPTED.name());
        assertThat(helpRequest.getAcceptedByUser()).isEqualTo(actor);
        assertThat(helpRequest.getAcceptedAt()).isNotNull();
        verify(auditService).log(eq(actor), eq(ActorSourceEnum.WEB), eq("ACCEPT_HELP_REQUEST"),
                eq("REMOTE_HELP_REQUEST"), eq(helpRequest.getId()), eq(AuditResultEnum.SUCCESS),
                eq(Map.of("requestedBy", "alice")));
    }

    @Test
    public void givenMissingHelpRequestAndMissingDeviceId_whenCreateRemoteSession_thenThrowBadRequestException() {
        User actor = createUser("admin");

        assertThatThrownBy(() -> remoteSupportService.createRemoteSession(
                new RemoteSessionCreateRequest(null, null, "assist", "rdp"),
                actor
        )).isInstanceOf(BadRequestException.class)
                .hasMessage("Pro vytvoření relace bez žádosti o pomoc je nutné zadat deviceId.");
    }

    private User createUser(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAdUsername(username);
        user.setDisplayName(username);
        return user;
    }

    private EndpointDevice createDevice(String hostname) {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname(hostname);
        return device;
    }
}
