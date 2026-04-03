package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.AgentHeartbeat;
import cz.fel.cvut.beevidence_and_cyber.dao.CommandExecution;
import cz.fel.cvut.beevidence_and_cyber.dao.CommandRequest;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.*;
import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.CommandStatusEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.CommandTypeEnum;
import cz.fel.cvut.beevidence_and_cyber.exception.NotFoundException;
import cz.fel.cvut.beevidence_and_cyber.repository.AgentHeartbeatRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.CommandExecutionRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.CommandRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommandService {

    private final CommandRequestRepository commandRequestRepository;
    private final CommandExecutionRepository commandExecutionRepository;
    private final AgentHeartbeatRepository agentHeartbeatRepository;
    private final DeviceService deviceService;
    private final ApiMapper apiMapper;
    private final AuditService auditService;

    public List<CommandRequestDto> getAllCommandRequests() {
        return commandRequestRepository.findAllByOrderByCreatedAtDesc().stream().map(apiMapper::toDto).toList();
    }

    public CommandRequestDto getCommandRequest(UUID id) {
        CommandRequest commandRequest = commandRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Command request with id " + id + " not found"));
        return apiMapper.toDto(commandRequest);
    }

    @Transactional
    public CommandRequestDto createCommandRequest(CommandRequestCreateRequest request, User actor) {
        EndpointDevice device = deviceService.findDevice(request.deviceId());
        CommandRequest commandRequest = new CommandRequest();
        commandRequest.setDevice(device);
        commandRequest.setRequestedByUser(actor);
        commandRequest.setCommandType(CommandTypeEnum.valueOf(request.commandType().toUpperCase()));
        commandRequest.setStatus(CommandStatusEnum.PENDING);
        commandRequest.setPayloadJson(request.payloadJson());
        commandRequest.setCreatedAt(LocalDateTime.now());
        CommandRequest saved = commandRequestRepository.save(commandRequest);
        auditService.log(actor, ActorSourceEnum.WEB, "CREATE_COMMAND_REQUEST", "COMMAND_REQUEST", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("commandType", saved.getCommandType().name()));
        return apiMapper.toDto(saved);
    }

    @Transactional
    public CommandExecutionDto createCommandExecution(CommandExecutionCreateRequest request, User actor) {
        CommandRequest commandRequest = commandRequestRepository.findById(request.commandRequestId())
                .orElseThrow(() -> new NotFoundException("Command request with id " + request.commandRequestId() + " not found"));

        CommandExecution execution = new CommandExecution();
        execution.setCommandRequest(commandRequest);
        if (request.agentHeartbeatId() != null) {
            AgentHeartbeat heartbeat = agentHeartbeatRepository.findById(request.agentHeartbeatId())
                    .orElseThrow(() -> new NotFoundException("Agent heartbeat with id " + request.agentHeartbeatId() + " not found"));
            execution.setAgentHeartbeat(heartbeat);
        }
        execution.setStartedAt(request.startedAt());
        execution.setFinishedAt(request.finishedAt());
        execution.setExitCode(request.exitCode());
        execution.setResultSummary(request.resultSummary());
        execution.setErrorMessage(request.errorMessage());

        if (request.finishedAt() != null) {
            commandRequest.setStatus(request.exitCode() != null && request.exitCode() == 0 ? CommandStatusEnum.SUCCESS : CommandStatusEnum.FAILED);
        } else if (request.startedAt() != null) {
            commandRequest.setStatus(CommandStatusEnum.RUNNING);
        } else {
            commandRequest.setStatus(CommandStatusEnum.SENT);
        }

        commandRequestRepository.save(commandRequest);
        CommandExecution saved = commandExecutionRepository.save(execution);
        auditService.log(actor, ActorSourceEnum.WEB, "CREATE_COMMAND_EXECUTION", "COMMAND_EXECUTION", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("commandRequestId", commandRequest.getId().toString()));
        return apiMapper.toDto(saved);
    }
}
