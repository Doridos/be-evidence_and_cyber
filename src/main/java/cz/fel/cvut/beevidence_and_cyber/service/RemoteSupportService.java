package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.ControlApproval;
import cz.fel.cvut.beevidence_and_cyber.dao.RemoteHelpRequest;
import cz.fel.cvut.beevidence_and_cyber.dao.RemoteSession;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.*;
import cz.fel.cvut.beevidence_and_cyber.enumeration.*;
import cz.fel.cvut.beevidence_and_cyber.exception.NotFoundException;
import cz.fel.cvut.beevidence_and_cyber.repository.ControlApprovalRepository;
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
        RemoteHelpRequest helpRequest = findHelpRequest(request.helpRequestId());
        RemoteSession session = new RemoteSession();
        session.setHelpRequest(helpRequest);
        session.setAdminUser(actor);
        session.setSessionType(RemoteSessionTypeEnum.valueOf(request.sessionType().toUpperCase()));
        session.setProvider(RemoteSessionProviderEnum.valueOf(request.provider().toUpperCase()));
        session.setStatus(RemoteSessionStatusEnum.CONNECTING);
        session.setStartedAt(LocalDateTime.now());
        RemoteSession saved = remoteSessionRepository.save(session);
        auditService.log(actor, ActorSourceEnum.WEB, "CREATE_REMOTE_SESSION", "REMOTE_SESSION", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("helpRequestId", helpRequest.getId().toString()));
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
}
