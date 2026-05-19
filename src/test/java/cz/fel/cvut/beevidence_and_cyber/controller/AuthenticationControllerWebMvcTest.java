package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.AuthResponse;
import cz.fel.cvut.beevidence_and_cyber.dto.LoginRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.UserDto;
import cz.fel.cvut.beevidence_and_cyber.service.AuthenticationService;
import cz.fel.cvut.beevidence_and_cyber.service.CurrentUserService;
import cz.fel.cvut.beevidence_and_cyber.service.DirectoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerWebMvcTest {

    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private DirectoryService directoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvcFor(new AuthenticationController(authenticationService, currentUserService, directoryService));
    }

    @Test
    public void givenValidLoginRequest_whenLogin_thenReturnAuthResponse() throws Exception {
        AuthResponse response = new AuthResponse(
                "jwt-token",
                new UserDto(UUID.randomUUID(), "alice", "Alice", "alice@example.test", "IT", true, null, "AD", List.of())
        );
        when(authenticationService.login(new LoginRequest("alice", "secret"))).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.user.adUsername").value("alice"));
    }

    @Test
    public void givenBlankPassword_whenLogin_thenReturnBadRequest() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("password")));
    }

    @Test
    public void givenCurrentUser_whenMe_thenReturnUserDto() throws Exception {
        User currentUser = new User();
        currentUser.setId(UUID.randomUUID());
        currentUser.setAdUsername("alice");
        UserDto userDto = new UserDto(currentUser.getId(), "alice", "Alice", null, null, true, null, "AD", List.of());
        when(currentUserService.requireCurrentUser()).thenReturn(currentUser);
        when(directoryService.toUserDto(currentUser)).thenReturn(userDto);

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adUsername").value("alice"));
    }
}
