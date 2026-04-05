package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dto.ControlApprovalRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.RemoteSessionCreateRequest;
import cz.fel.cvut.beevidence_and_cyber.service.CurrentUserService;
import cz.fel.cvut.beevidence_and_cyber.service.RemoteSupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RemoteSupportController {

    private final RemoteSupportService remoteSupportService;
    private final CurrentUserService currentUserService;

    @GetMapping("/remote-help-requests")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getAllHelpRequests() {
        return remoteSupportService.getAllHelpRequests();
    }

    @GetMapping("/remote-help-requests/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public Object getHelpRequest(@PathVariable UUID id) {
        return remoteSupportService.getHelpRequest(id);
    }

    @PostMapping("/remote-help-requests/{id}/accept")
    @PreAuthorize("hasRole('ADMIN')")
    public Object acceptHelpRequest(@PathVariable UUID id) {
        return remoteSupportService.acceptHelpRequest(id, currentUserService.requireCurrentUser());
    }

    @GetMapping("/remote-sessions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getAllRemoteSessions() {
        return remoteSupportService.getAllRemoteSessions();
    }

    @PostMapping("/remote-sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public Object createRemoteSession(@Valid @RequestBody RemoteSessionCreateRequest request) {
        return remoteSupportService.createRemoteSession(request, currentUserService.requireCurrentUser());
    }

    @PostMapping("/remote-sessions/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public Object completeRemoteSession(@PathVariable UUID id) {
        return remoteSupportService.completeRemoteSession(id, currentUserService.requireCurrentUser());
    }

    @PostMapping("/remote-sessions/{id}/approvals")
    @PreAuthorize("hasRole('ADMIN')")
    public Object createControlApproval(@PathVariable UUID id, @Valid @RequestBody ControlApprovalRequest request) {
        return remoteSupportService.createControlApproval(id, request, currentUserService.requireCurrentUser());
    }
}
