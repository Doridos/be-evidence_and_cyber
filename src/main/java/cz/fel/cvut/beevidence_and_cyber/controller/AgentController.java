package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dto.AgentCommandExecutionRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.AgentHeartbeatRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.AgentHelpRequestInput;
import cz.fel.cvut.beevidence_and_cyber.dto.AgentLogIngestionRequest;
import cz.fel.cvut.beevidence_and_cyber.service.AgentIngestionService;
import cz.fel.cvut.beevidence_and_cyber.service.CommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agents")
public class AgentController {

    private final AgentIngestionService agentIngestionService;
    private final CommandService commandService;

    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(@Valid @RequestBody AgentHeartbeatRequest request) {
        return ResponseEntity.ok(agentIngestionService.ingestHeartbeat(request));
    }

    @PostMapping("/help-requests")
    public ResponseEntity<?> helpRequest(@Valid @RequestBody AgentHelpRequestInput request) {
        return ResponseEntity.ok(agentIngestionService.ingestHelpRequest(request));
    }

    @PostMapping("/logs")
    public ResponseEntity<?> logs(@Valid @RequestBody AgentLogIngestionRequest request) {
        agentIngestionService.ingestLogs(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/command-executions")
    public ResponseEntity<?> commandExecution(@Valid @RequestBody AgentCommandExecutionRequest request) {
        return ResponseEntity.ok(agentIngestionService.ingestCommandExecution(request));
    }

    @GetMapping("/commands/pending")
    public ResponseEntity<?> getPendingCommands(@RequestParam String deviceHostname) {
        return ResponseEntity.ok(commandService.claimPendingCommands(deviceHostname));
    }
}
