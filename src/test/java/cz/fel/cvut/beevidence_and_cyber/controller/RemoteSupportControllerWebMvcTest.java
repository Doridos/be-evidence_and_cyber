package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.RemoteHelpRequestDto;
import cz.fel.cvut.beevidence_and_cyber.service.CurrentUserService;
import cz.fel.cvut.beevidence_and_cyber.service.RemoteSupportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RemoteSupportControllerWebMvcTest {

    @Mock
    private RemoteSupportService remoteSupportService;
    @Mock
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvcFor(new RemoteSupportController(remoteSupportService, currentUserService));
    }

    @Test
    public void givenHelpRequests_whenGetAllHelpRequests_thenReturnRequestList() throws Exception {
        when(remoteSupportService.getAllHelpRequests()).thenReturn(List.of(
                new RemoteHelpRequestDto(UUID.randomUUID(), UUID.randomUUID(), "pc-01", null, null, "pc-01", null,
                        "alice", "Alice", "Need help", "NEW", LocalDateTime.now(), null, null)
        ));

        mockMvc.perform(get("/remote-help-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("NEW"));
    }

    @Test
    public void givenBlankDecision_whenCreateControlApproval_thenReturnBadRequest() throws Exception {
        mockMvc.perform(post("/remote-sessions/11111111-1111-1111-1111-111111111111/approvals")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("decision")));
    }
}
