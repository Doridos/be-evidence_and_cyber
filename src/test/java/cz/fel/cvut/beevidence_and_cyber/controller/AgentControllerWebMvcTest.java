package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dto.AgentPendingCommandDto;
import cz.fel.cvut.beevidence_and_cyber.dto.AgentHelpRequestInput;
import cz.fel.cvut.beevidence_and_cyber.service.AgentIngestionService;
import cz.fel.cvut.beevidence_and_cyber.service.CommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgentControllerWebMvcTest {

    @Mock
    private AgentIngestionService agentIngestionService;
    @Mock
    private CommandService commandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvcFor(new AgentController(agentIngestionService, commandService));
    }

    @Test
    public void givenPendingCommandsRequest_whenGetPendingCommands_thenReturnCommandList() throws Exception {
        when(commandService.claimPendingCommands("pc-01")).thenReturn(List.of(
                new AgentPendingCommandDto(UUID.randomUUID(), "COLLECT", java.util.Map.of("scope", "logs"), LocalDateTime.now())
        ));

        mockMvc.perform(get("/agents/commands/pending").param("deviceHostname", "pc-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].commandType").value("COLLECT"));
    }

    @Test
    public void givenBlankRequestedByUsername_whenHelpRequest_thenReturnBadRequest() throws Exception {
        mockMvc.perform(post("/agents/help-requests")
                        .contentType(APPLICATION_JSON)
                        .content(ControllerTestSupport.json(new AgentHelpRequestInput(
                                null, " ", "Alice", null, "pc-01", null, null, "Need help", null
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("requestedByUsername")));
    }
}
