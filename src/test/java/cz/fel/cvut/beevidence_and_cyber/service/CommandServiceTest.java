package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.CommandExecution;
import cz.fel.cvut.beevidence_and_cyber.dao.CommandRequest;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.AgentPendingCommandDto;
import cz.fel.cvut.beevidence_and_cyber.dto.CommandExecutionCreateRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.CommandExecutionDto;
import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.CommandStatusEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.CommandTypeEnum;
import cz.fel.cvut.beevidence_and_cyber.repository.AgentHeartbeatRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.CommandExecutionRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.CommandRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandServiceTest {

    @Mock
    private CommandRequestRepository commandRequestRepository;
    @Mock
    private CommandExecutionRepository commandExecutionRepository;
    @Mock
    private AgentHeartbeatRepository agentHeartbeatRepository;
    @Mock
    private DeviceService deviceService;
    @Mock
    private AuditService auditService;

    private CommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new CommandService(
                commandRequestRepository,
                commandExecutionRepository,
                agentHeartbeatRepository,
                deviceService,
                new ApiMapper(),
                auditService
        );
    }

    @Test
    public void givenPendingCommands_whenClaimPendingCommands_thenReturnCommandsAndMarkThemAsSent() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname("pc-01");
        CommandRequest firstCommand = createCommandRequest(device, CommandStatusEnum.PENDING, CommandTypeEnum.COLLECT);
        CommandRequest secondCommand = createCommandRequest(device, CommandStatusEnum.PENDING, CommandTypeEnum.USB);
        when(deviceService.findDeviceByHostname("pc-01")).thenReturn(device);
        when(commandRequestRepository.findByDeviceAndStatusOrderByCreatedAtAsc(device, CommandStatusEnum.PENDING))
                .thenReturn(List.of(firstCommand, secondCommand));

        List<AgentPendingCommandDto> result = commandService.claimPendingCommands("pc-01");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AgentPendingCommandDto::commandType)
                .containsExactly(CommandTypeEnum.COLLECT.name(), CommandTypeEnum.USB.name());
        assertThat(firstCommand.getStatus()).isEqualTo(CommandStatusEnum.SENT);
        assertThat(secondCommand.getStatus()).isEqualTo(CommandStatusEnum.SENT);
        verify(commandRequestRepository).saveAll(List.of(firstCommand, secondCommand));
    }

    @Test
    public void givenFinishedExecutionWithZeroExitCode_whenCreateCommandExecution_thenReturnSuccessfulExecution() {
        User actor = createUser("admin");
        CommandRequest commandRequest = createCommandRequest(createDevice("pc-01"), CommandStatusEnum.SENT, CommandTypeEnum.COLLECT);
        commandRequest.setId(UUID.randomUUID());
        LocalDateTime startedAt = LocalDateTime.now().minusMinutes(1);
        LocalDateTime finishedAt = LocalDateTime.now();
        CommandExecution savedExecution = new CommandExecution();
        savedExecution.setId(UUID.randomUUID());
        savedExecution.setCommandRequest(commandRequest);
        savedExecution.setStartedAt(startedAt);
        savedExecution.setFinishedAt(finishedAt);
        savedExecution.setExitCode(0);
        savedExecution.setResultSummary("Completed");
        when(commandRequestRepository.findById(commandRequest.getId())).thenReturn(Optional.of(commandRequest));
        when(commandExecutionRepository.save(any(CommandExecution.class))).thenReturn(savedExecution);

        CommandExecutionDto result = commandService.createCommandExecution(new CommandExecutionCreateRequest(
                commandRequest.getId(),
                null,
                startedAt,
                finishedAt,
                0,
                "Completed",
                null,
                Map.of("ok", true)
        ), actor);

        assertThat(result.commandRequestId()).isEqualTo(commandRequest.getId());
        assertThat(result.exitCode()).isZero();
        assertThat(commandRequest.getStatus()).isEqualTo(CommandStatusEnum.SUCCESS);

        ArgumentCaptor<CommandExecution> executionCaptor = ArgumentCaptor.forClass(CommandExecution.class);
        verify(commandExecutionRepository).save(executionCaptor.capture());
        assertThat(executionCaptor.getValue().getResultSummary()).isEqualTo("Completed");
        verify(auditService).log(eq(actor), eq(ActorSourceEnum.WEB), eq("CREATE_COMMAND_EXECUTION"), eq("COMMAND_EXECUTION"),
                eq(savedExecution.getId()), eq(AuditResultEnum.SUCCESS),
                eq(Map.of("commandRequestId", commandRequest.getId().toString())));
    }

    @Test
    public void givenStartedExecutionWithoutFinish_whenCreateCommandExecution_thenReturnRunningExecution() {
        User actor = createUser("admin");
        CommandRequest commandRequest = createCommandRequest(createDevice("pc-01"), CommandStatusEnum.SENT, CommandTypeEnum.COLLECT);
        commandRequest.setId(UUID.randomUUID());
        LocalDateTime startedAt = LocalDateTime.now().minusSeconds(10);
        CommandExecution savedExecution = new CommandExecution();
        savedExecution.setId(UUID.randomUUID());
        savedExecution.setCommandRequest(commandRequest);
        savedExecution.setStartedAt(startedAt);
        when(commandRequestRepository.findById(commandRequest.getId())).thenReturn(Optional.of(commandRequest));
        when(commandExecutionRepository.save(any(CommandExecution.class))).thenReturn(savedExecution);

        CommandExecutionDto result = commandService.createCommandExecution(new CommandExecutionCreateRequest(
                commandRequest.getId(),
                null,
                startedAt,
                null,
                null,
                null,
                null,
                null
        ), actor);

        assertThat(result.startedAt()).isEqualTo(startedAt);
        assertThat(commandRequest.getStatus()).isEqualTo(CommandStatusEnum.RUNNING);
    }

    private CommandRequest createCommandRequest(EndpointDevice device, CommandStatusEnum status, CommandTypeEnum type) {
        CommandRequest commandRequest = new CommandRequest();
        commandRequest.setId(UUID.randomUUID());
        commandRequest.setDevice(device);
        commandRequest.setRequestedByUser(createUser("requester"));
        commandRequest.setStatus(status);
        commandRequest.setCommandType(type);
        commandRequest.setCreatedAt(LocalDateTime.now());
        return commandRequest;
    }

    private EndpointDevice createDevice(String hostname) {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname(hostname);
        return device;
    }

    private User createUser(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAdUsername(username);
        user.setDisplayName(username);
        return user;
    }
}
