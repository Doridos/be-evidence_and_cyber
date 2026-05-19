package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionFindingDto;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionRuleDto;
import cz.fel.cvut.beevidence_and_cyber.service.AiAnalysisService;
import cz.fel.cvut.beevidence_and_cyber.service.CurrentUserService;
import cz.fel.cvut.beevidence_and_cyber.service.DetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DetectionControllerWebMvcTest {

    @Mock
    private DetectionService detectionService;
    @Mock
    private AiAnalysisService aiAnalysisService;
    @Mock
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvcFor(new DetectionController(detectionService, aiAnalysisService, currentUserService));
    }

    @Test
    public void givenRules_whenGetAllRules_thenReturnRuleList() throws Exception {
        when(detectionService.getAllRules()).thenReturn(List.of(
                new DetectionRuleDto(UUID.randomUUID(), "USB_DEVICE_CONNECTED", "USB", "desc", "HIGH", "LOG", Map.of("kind", "event_code"), true)
        ));

        mockMvc.perform(get("/detection-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("USB_DEVICE_CONNECTED"));
    }

    @Test
    public void givenValidStatusRequest_whenUpdateFindingStatus_thenReturnUpdatedFinding() throws Exception {
        User actor = new User();
        actor.setId(UUID.randomUUID());
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(detectionService.updateFindingStatus(any(), any(), eq(actor))).thenReturn(new DetectionFindingDto(
                UUID.randomUUID(), UUID.randomUUID(), "pc-01", UUID.randomUUID(), "RULE-1", "RESOLVED", "HIGH",
                "title", "desc", LocalDateTime.now().minusMinutes(5), LocalDateTime.now(), false, Map.of(), List.of()
        ));

        mockMvc.perform(put("/detection-findings/11111111-1111-1111-1111-111111111111/status")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "resolved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }
}
