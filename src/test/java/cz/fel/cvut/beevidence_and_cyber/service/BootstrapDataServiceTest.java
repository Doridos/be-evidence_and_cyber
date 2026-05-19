package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.DetectionRule;
import cz.fel.cvut.beevidence_and_cyber.dao.Role;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionRuleRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapDataServiceTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private DetectionRuleRepository detectionRuleRepository;

    private BootstrapDataService bootstrapDataService;

    @BeforeEach
    void setUp() {
        bootstrapDataService = new BootstrapDataService(roleRepository, detectionRuleRepository);
    }

    @Test
    public void givenMissingBootstrapData_whenRun_thenCreateDefaultRolesAndRules() {
        when(roleRepository.findByCodeIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(detectionRuleRepository.findByCodeIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(detectionRuleRepository.save(any(DetectionRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bootstrapDataService.run();

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, atLeastOnce()).save(roleCaptor.capture());
        assertThat(roleCaptor.getAllValues()).extracting(Role::getCode).contains("USER", "MANAGER", "ADMIN");
        verify(detectionRuleRepository, atLeastOnce()).save(any(DetectionRule.class));
    }
}
