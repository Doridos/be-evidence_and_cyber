package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.ControlApproval;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dao.RemoteHelpRequest;
import cz.fel.cvut.beevidence_and_cyber.dao.RemoteSession;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.*;
import cz.fel.cvut.beevidence_and_cyber.enumeration.*;
import cz.fel.cvut.beevidence_and_cyber.exception.BadRequestException;
import cz.fel.cvut.beevidence_and_cyber.exception.NotFoundException;
import cz.fel.cvut.beevidence_and_cyber.repository.ControlApprovalRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.EndpointDeviceRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.RemoteHelpRequestRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.RemoteSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RemoteSupportService {

    private final RemoteHelpRequestRepository remoteHelpRequestRepository;
    private final RemoteSessionRepository remoteSessionRepository;
    private final ControlApprovalRepository controlApprovalRepository;
    private final EndpointDeviceRepository endpointDeviceRepository;
    private final ApiMapper apiMapper;
    private final AuditService auditService;

    public List<RemoteHelpRequestDto> getAllHelpRequests() {
        return remoteHelpRequestRepository.findAllByOrderByRequestedAtDesc().stream().map(apiMapper::toDto).toList();
    }

    public RemoteHelpRequestDto getHelpRequest(UUID id) {
        return apiMapper.toDto(findHelpRequest(id));
    }

    @Transactional
    public RemoteHelpRequestDto acceptHelpRequest(UUID id, User actor) {
        RemoteHelpRequest request = findHelpRequest(id);
        request.setStatus(HelpRequestStatusEnum.ACCEPTED);
        request.setAcceptedByUser(actor);
        request.setAcceptedAt(LocalDateTime.now());
        RemoteHelpRequest saved = remoteHelpRequestRepository.save(request);
        auditService.log(actor, ActorSourceEnum.WEB, "ACCEPT_HELP_REQUEST", "REMOTE_HELP_REQUEST", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("requestedBy", saved.getRequestedByUsername()));
        return apiMapper.toDto(saved);
    }

    public List<RemoteSessionDto> getAllRemoteSessions() {
        return remoteSessionRepository.findAllByOrderByStartedAtDesc().stream().map(apiMapper::toDto).toList();
    }

    @Transactional
    public RemoteSessionDto createRemoteSession(RemoteSessionCreateRequest request, User actor) {
        RemoteHelpRequest helpRequest = request.helpRequestId() == null ? null : findHelpRequest(request.helpRequestId());
        EndpointDevice device = resolveRemoteSessionDevice(request, helpRequest);

        RemoteSession session = new RemoteSession();
        session.setHelpRequest(helpRequest);
        session.setDevice(device);
        session.setAdminUser(actor);
        session.setSessionType(RemoteSessionTypeEnum.valueOf(request.sessionType().toUpperCase()));
        session.setProvider(RemoteSessionProviderEnum.valueOf(request.provider().toUpperCase()));
        session.setStatus(RemoteSessionStatusEnum.ACTIVE);
        session.setStartedAt(LocalDateTime.now());
        RemoteSession saved = remoteSessionRepository.save(session);

        if (helpRequest != null && helpRequest.getStatus() == HelpRequestStatusEnum.NEW) {
            helpRequest.setStatus(HelpRequestStatusEnum.ACCEPTED);
            helpRequest.setAcceptedByUser(actor);
            helpRequest.setAcceptedAt(LocalDateTime.now());
            remoteHelpRequestRepository.save(helpRequest);
        }

        auditService.log(actor, ActorSourceEnum.WEB, "CREATE_REMOTE_SESSION", "REMOTE_SESSION", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of(
                        "helpRequestId", helpRequest == null ? "" : helpRequest.getId().toString(),
                        "deviceId", device.getId().toString(),
                        "deviceHostname", device.getHostname()
                ));
        return apiMapper.toDto(saved);
    }

    @Transactional
    public RemoteSessionDto completeRemoteSession(UUID remoteSessionId, User actor) {
        RemoteSession session = remoteSessionRepository.findById(remoteSessionId)
                .orElseThrow(() -> new NotFoundException("Remote session with id " + remoteSessionId + " not found"));

        session.setStatus(RemoteSessionStatusEnum.ENDED);
        session.setEndedAt(LocalDateTime.now());
        RemoteSession saved = remoteSessionRepository.save(session);

        if (saved.getHelpRequest() != null && saved.getHelpRequest().getStatus() != HelpRequestStatusEnum.CLOSED) {
            saved.getHelpRequest().setStatus(HelpRequestStatusEnum.CLOSED);
            remoteHelpRequestRepository.save(saved.getHelpRequest());
        }

        auditService.log(actor, ActorSourceEnum.WEB, "COMPLETE_REMOTE_SESSION", "REMOTE_SESSION", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("deviceHostname", saved.getDevice().getHostname()));
        return apiMapper.toDto(saved);
    }

    @Transactional
    public ControlApprovalDto createControlApproval(UUID remoteSessionId, ControlApprovalRequest request, User actor) {
        RemoteSession session = remoteSessionRepository.findById(remoteSessionId)
                .orElseThrow(() -> new NotFoundException("Remote session with id " + remoteSessionId + " not found"));
        ControlApproval approval = new ControlApproval();
        approval.setRemoteSession(session);
        approval.setRequestedAt(LocalDateTime.now());
        approval.setDecision(ApprovalDecisionEnum.valueOf(request.decision().toUpperCase()));
        approval.setDecidedByUsername(request.decidedByUsername());
        approval.setDecidedAt(LocalDateTime.now());
        approval.setNote(request.note());
        ControlApproval saved = controlApprovalRepository.save(approval);
        auditService.log(actor, ActorSourceEnum.WEB, "CREATE_CONTROL_APPROVAL", "CONTROL_APPROVAL", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("decision", saved.getDecision().name()));
        return apiMapper.toDto(saved);
    }

    private RemoteHelpRequest findHelpRequest(UUID id) {
        return remoteHelpRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Remote help request with id " + id + " not found"));
    }

    private EndpointDevice resolveRemoteSessionDevice(RemoteSessionCreateRequest request, RemoteHelpRequest helpRequest) {
        if (helpRequest != null) {
            return helpRequest.getDevice();
        }
        if (request.deviceId() == null) {
            throw new BadRequestException("Pro vytvoření relace bez žádosti o pomoc je nutné zadat deviceId.");
        }
        return endpointDeviceRepository.findById(request.deviceId())
                .orElseThrow(() -> new NotFoundException("Device with id " + request.deviceId() + " not found"));
    }
}
