package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.DeviceDetailDto;
import cz.fel.cvut.beevidence_and_cyber.dto.DeviceOwnerOptionDto;
import cz.fel.cvut.beevidence_and_cyber.exception.GlobalExceptionHandler;
import cz.fel.cvut.beevidence_and_cyber.service.AiAnalysisService;
import cz.fel.cvut.beevidence_and_cyber.service.CommandService;
import cz.fel.cvut.beevidence_and_cyber.service.CurrentUserService;
import cz.fel.cvut.beevidence_and_cyber.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeviceControllerWebMvcTest {

    @Mock
    private DeviceService deviceService;
    @Mock
    private CommandService commandService;
    @Mock
    private AiAnalysisService aiAnalysisService;
    @Mock
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DeviceController controller = new DeviceController(deviceService, commandService, aiAnalysisService, currentUserService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    public void givenValidOwnerRequest_whenCreateOwner_thenReturnCreatedOwner() throws Exception {
        User currentUser = new User();
        currentUser.setId(UUID.randomUUID());
        DeviceOwnerOptionDto owner = new DeviceOwnerOptionDto(UUID.randomUUID(), "Alice", "Smith", "Alice Smith");
        when(currentUserService.requireCurrentUser()).thenReturn(currentUser);
        when(deviceService.createOwner(any(), eq(currentUser))).thenReturn(owner);

        mockMvc.perform(post("/devices/owners")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Alice",
                                  "lastName": "Smith"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.displayName").value("Alice Smith"));

        verify(deviceService).createOwner(any(), eq(currentUser));
    }

    @Test
    public void givenBlankOwnerFirstName_whenCreateOwner_thenReturnBadRequest() throws Exception {
        mockMvc.perform(post("/devices/owners")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": " ",
                                  "lastName": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("firstName")));
    }

    @Test
    public void givenExistingDevices_whenGetAllDevices_thenReturnDeviceList() throws Exception {
        DeviceDetailDto firstDevice = new DeviceDetailDto(
                UUID.randomUUID(), "AT-1", "INV-1", "pc-01", null, null, null, null,
                null, null, null, null, "ACTIVE", true, false, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of()
        );
        DeviceDetailDto secondDevice = new DeviceDetailDto(
                UUID.randomUUID(), "AT-2", "INV-2", "pc-02", null, null, null, null,
                null, null, null, null, "UNREACHABLE", true, false, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(deviceService.getAllDevices()).thenReturn(List.of(firstDevice, secondDevice));

        mockMvc.perform(get("/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hostname").value("pc-01"))
                .andExpect(jsonPath("$[1].status").value("UNREACHABLE"));
    }
}
