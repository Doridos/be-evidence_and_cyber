package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dao.RetentionSettings;
import cz.fel.cvut.beevidence_and_cyber.dto.PurgeResultDto;
import cz.fel.cvut.beevidence_and_cyber.dto.SystemCapacityDto;
import cz.fel.cvut.beevidence_and_cyber.dto.TableSizeEntryDto;
import cz.fel.cvut.beevidence_and_cyber.service.DataRetentionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SystemControllerWebMvcTest {

    @Mock
    private DataRetentionService dataRetentionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvcFor(new SystemController(dataRetentionService));
    }

    @Test
    public void givenCapacityRequest_whenGetCapacity_thenReturnSystemCapacity() throws Exception {
        when(dataRetentionService.getSystemCapacity()).thenReturn(new SystemCapacityDto(
                1024L, 4096L, 25.0, List.of(new TableSizeEntryDto("device_log_entry", 512L)), 30, 1, true, null
        ));

        mockMvc.perform(get("/system/capacity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dbSizeBytes").value(1024))
                .andExpect(jsonPath("$.tables[0].tableName").value("device_log_entry"));
    }

    @Test
    public void givenPurgeRequest_whenRunPurge_thenReturnPurgeSummary() throws Exception {
        when(dataRetentionService.runPurge()).thenReturn(new PurgeResultDto(
                true, LocalDateTime.now(), Map.of("logs", 5L), 5L, "Purge dokončen"
        ));

        mockMvc.perform(post("/system/retention/purge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeleted").value(5))
                .andExpect(jsonPath("$.message").value("Purge dokončen"));
    }
}
