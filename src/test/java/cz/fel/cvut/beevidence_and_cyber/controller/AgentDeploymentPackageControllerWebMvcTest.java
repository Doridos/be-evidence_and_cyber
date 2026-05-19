package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.service.AgentDeploymentPackageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgentDeploymentPackageControllerWebMvcTest {

    @Mock
    private AgentDeploymentPackageService packageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvcFor(new AgentDeploymentPackageController(packageService));
    }

    @Test
    public void givenPackageToken_whenDownloadPackage_thenReturnAttachmentHeaders() throws Exception {
        when(packageService.resolvePackage("token-1")).thenReturn(new ByteArrayResource("zip".getBytes()));

        mockMvc.perform(get("/deployment-packages/token-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"evidence-agent-package.zip\""));
    }
}
