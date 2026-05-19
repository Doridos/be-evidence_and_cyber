package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.exception.NotFoundException;
import cz.fel.cvut.beevidence_and_cyber.security.ApplicationUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private DirectoryService directoryService;

    private CurrentUserService currentUserService;

    @BeforeEach
    void setUp() {
        currentUserService = new CurrentUserService(directoryService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void givenAuthenticatedPrincipal_whenRequireCurrentUser_thenReturnResolvedUser() {
        User persistedUser = createUser("alice");
        ApplicationUserPrincipal principal = new ApplicationUserPrincipal(
                persistedUser,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "secret", principal.getAuthorities())
        );
        when(directoryService.findUserByUsername("alice")).thenReturn(persistedUser);

        User result = currentUserService.requireCurrentUser();

        assertThat(result).isEqualTo(persistedUser);
    }

    @Test
    public void givenMissingAuthentication_whenRequireCurrentUser_thenThrowNotFoundException() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> currentUserService.requireCurrentUser())
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Authenticated user not found.");
    }

    private User createUser(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAdUsername(username);
        user.setDisplayName(username);
        user.setEnabled(true);
        return user;
    }
}
