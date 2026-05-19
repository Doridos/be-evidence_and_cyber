package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.config.LdapProperties;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.AuthResponse;
import cz.fel.cvut.beevidence_and_cyber.dto.LoginRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.UserDto;
import cz.fel.cvut.beevidence_and_cyber.security.AdUserPrincipal;
import cz.fel.cvut.beevidence_and_cyber.security.ApplicationUserPrincipal;
import cz.fel.cvut.beevidence_and_cyber.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private DirectoryService directoryService;
    @Mock
    private JwtService jwtService;

    private LdapProperties ldapProperties;
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        ldapProperties = new LdapProperties();
        authenticationService = new AuthenticationService(authenticationManager, directoryService, jwtService, ldapProperties);
    }

    @Test
    public void givenUserOutsideRequiredGroup_whenLogin_thenThrowAccessDeniedException() {
        ldapProperties.setRequiredGroup("Security-Team");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "alice",
                "secret",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        assertThatThrownBy(() -> authenticationService.login(new LoginRequest("alice", "secret")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Security-Team");
    }

    @Test
    public void givenAdPrincipalInRequiredGroup_whenLogin_thenReturnJwtAndUserDto() {
        ldapProperties.setRequiredGroup("CN=Admins,OU=Groups,DC=test,DC=local");
        AdUserPrincipal adPrincipal = new AdUserPrincipal(
                "alice",
                "Alice Smith",
                "alice@example.test",
                "IT",
                List.of(new SimpleGrantedAuthority("CN=Admins,OU=Groups,DC=test,DC=local"))
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(adPrincipal, "secret", adPrincipal.getAuthorities());
        User user = createUser("alice", "Alice Smith");
        ApplicationUserPrincipal applicationUserPrincipal = new ApplicationUserPrincipal(
                user,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        UserDto userDto = new UserDto(user.getId(), "alice", "Alice Smith", "alice@example.test", "IT", true, null, "AD", List.of());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(directoryService.ensureUserExists("alice", "Alice Smith", "alice@example.test", "IT")).thenReturn(user);
        when(directoryService.loadPrincipalByUsername("alice")).thenReturn(applicationUserPrincipal);
        when(directoryService.toUserDto(user)).thenReturn(userDto);
        when(jwtService.generateToken(applicationUserPrincipal)).thenReturn("jwt-token");

        AuthResponse result = authenticationService.login(new LoginRequest("alice", "secret"));

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.user()).isEqualTo(userDto);
        verify(directoryService).updateLastLogin(user);
    }

    private User createUser(String username, String displayName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAdUsername(username);
        user.setDisplayName(displayName);
        user.setEnabled(true);
        return user;
    }
}
