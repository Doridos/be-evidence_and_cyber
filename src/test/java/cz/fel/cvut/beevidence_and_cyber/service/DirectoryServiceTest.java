package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.Role;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dao.UserRoleAssignment;
import cz.fel.cvut.beevidence_and_cyber.repository.RoleRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.UserRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.UserRoleAssignmentRepository;
import cz.fel.cvut.beevidence_and_cyber.security.ApplicationUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectoryServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock
    private AuditService auditService;

    private DirectoryService directoryService;

    @BeforeEach
    void setUp() {
        directoryService = new DirectoryService(
                userRepository,
                roleRepository,
                userRoleAssignmentRepository,
                new ApiMapper(),
                auditService
        );
    }

    @Test
    public void givenUnknownUsername_whenEnsureUserExists_thenCreateUserAndAssignDefaultRole() {
        Role defaultRole = createRole("USER");
        when(userRepository.findByAdUsernameIgnoreCase("alice")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            return user;
        });
        when(roleRepository.findByCodeIgnoreCase("USER")).thenReturn(Optional.of(defaultRole));
        when(userRoleAssignmentRepository.findByUser(any(User.class))).thenReturn(List.of());

        User result = directoryService.ensureUserExists("alice", "Alice Smith", "alice@example.test", "IT");

        assertThat(result.getAdUsername()).isEqualTo("alice");
        assertThat(result.getDisplayName()).isEqualTo("Alice Smith");
        assertThat(result.getEmail()).isEqualTo("alice@example.test");
        assertThat(result.getDepartment()).isEqualTo("IT");

        ArgumentCaptor<UserRoleAssignment> captor = ArgumentCaptor.forClass(UserRoleAssignment.class);
        verify(userRoleAssignmentRepository).save(captor.capture());
        assertThat(captor.getValue().getRole().getCode()).isEqualTo("USER");
        assertThat(captor.getValue().getUser()).isEqualTo(result);
    }

    @Test
    public void givenUserWithRoleAssignments_whenLoadPrincipalByUsername_thenReturnAuthoritiesFromDistinctRoles() {
        User user = createUser("alice");
        Role userRole = createRole("USER");
        Role adminRole = createRole("ADMIN");
        when(userRepository.findByAdUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findByUser(user)).thenReturn(List.of(
                createAssignment(user, userRole),
                createAssignment(user, adminRole),
                createAssignment(user, userRole)
        ));

        ApplicationUserPrincipal principal = directoryService.loadPrincipalByUsername("alice");

        assertThat(principal.getUsername()).isEqualTo("alice");
        assertThat(principal.getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    private User createUser(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAdUsername(username);
        user.setDisplayName(username);
        user.setEnabled(true);
        return user;
    }

    private Role createRole(String code) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setCode(code);
        role.setName(code);
        return role;
    }

    private UserRoleAssignment createAssignment(User user, Role role) {
        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setUser(user);
        assignment.setRole(role);
        return assignment;
    }
}
