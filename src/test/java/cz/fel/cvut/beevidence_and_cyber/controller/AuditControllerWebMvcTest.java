package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dto.AuditLogDto;
import cz.fel.cvut.beevidence_and_cyber.service.AuditService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuditControllerWebMvcTest {

    @Mock
    private AuditService auditService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvcFor(new AuditController(auditService));
    }

    @Test
    public void givenAuditLogs_whenGetAllAuditLogs_thenReturnAuditLogList() throws Exception {
        when(auditService.getAll()).thenReturn(List.of(
                new AuditLogDto(UUID.randomUUID(), UUID.randomUUID(), "WEB", "UPDATE_DEVICE", "DEVICE", UUID.randomUUID(),
                        "SUCCESS", LocalDateTime.now(), java.util.Map.of("hostname", "pc-01"))
        ));

        mockMvc.perform(get("/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actionType").value("UPDATE_DEVICE"));
    }
}
