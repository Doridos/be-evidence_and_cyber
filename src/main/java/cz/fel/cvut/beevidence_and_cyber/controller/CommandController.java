package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dto.CommandExecutionCreateRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.CommandRequestCreateRequest;
import cz.fel.cvut.beevidence_and_cyber.service.CommandService;
import cz.fel.cvut.beevidence_and_cyber.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CommandController {

    private final CommandService commandService;
    private final CurrentUserService currentUserService;

    @GetMapping("/command-requests")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getAllCommandRequests() {
        return commandService.getAllCommandRequests();
    }

    @GetMapping("/command-requests/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public Object getCommandRequest(@PathVariable UUID id) {
        return commandService.getCommandRequest(id);
    }

    @PostMapping("/command-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public Object createCommandRequest(@Valid @RequestBody CommandRequestCreateRequest request) {
        return commandService.createCommandRequest(request, currentUserService.requireCurrentUser());
    }

    @PostMapping("/command-executions")
    @PreAuthorize("hasRole('ADMIN')")
    public Object createCommandExecution(@Valid @RequestBody CommandExecutionCreateRequest request) {
        return commandService.createCommandExecution(request, currentUserService.requireCurrentUser());
    }
}
