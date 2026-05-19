package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.CommandRequestDto;
import cz.fel.cvut.beevidence_and_cyber.service.CommandService;
import cz.fel.cvut.beevidence_and_cyber.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CommandControllerWebMvcTest {

    @Mock
    private CommandService commandService;
    @Mock
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvcFor(new CommandController(commandService, currentUserService));
    }

    @Test
    public void givenValidCommandRequest_whenCreateCommandRequest_thenReturnCreatedRequest() throws Exception {
        User actor = new User();
        actor.setId(UUID.randomUUID());
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(commandService.createCommandRequest(any(), eq(actor))).thenReturn(new CommandRequestDto(
                UUID.randomUUID(),
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "pc-01",
                actor.getId(),
                null,
                "COLLECT",
                "PENDING",
                Map.of("scope", "logs"),
                LocalDateTime.now(),
                null
        ));

        mockMvc.perform(post("/command-requests")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "11111111-1111-1111-1111-111111111111",
                                  "commandType": "COLLECT",
                                  "payloadJson": {
                                    "scope": "logs"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.commandType").value("COLLECT"));
    }

    @Test
    public void givenBlankCommandType_whenCreateCommandRequest_thenReturnBadRequest() throws Exception {
        mockMvc.perform(post("/command-requests")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "11111111-1111-1111-1111-111111111111",
                                  "commandType": " ",
                                  "payloadJson": {
                                    "scope": "logs"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("commandType")));
    }
}
