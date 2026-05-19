package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.AuditLog;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.AuditLogDto;
import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import cz.fel.cvut.beevidence_and_cyber.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository, new ApiMapper());
    }

    @Test
    public void givenAuditLogInput_whenLog_thenPersistAuditEntry() {
        User actor = createUser("admin");
        UUID targetId = UUID.randomUUID();
        Map<String, Object> details = Map.of("hostname", "pc-01");
        AuditLog savedAuditLog = new AuditLog();
        savedAuditLog.setId(UUID.randomUUID());
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedAuditLog);

        AuditLog result = auditService.log(actor, ActorSourceEnum.WEB, "CREATE_DEVICE", "DEVICE", targetId, AuditResultEnum.SUCCESS, details);

        assertThat(result).isSameAs(savedAuditLog);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog persisted = captor.getValue();
        assertThat(persisted.getActorUser()).isEqualTo(actor);
        assertThat(persisted.getActorSource()).isEqualTo(ActorSourceEnum.WEB);
        assertThat(persisted.getActionType()).isEqualTo("CREATE_DEVICE");
        assertThat(persisted.getTargetType()).isEqualTo("DEVICE");
        assertThat(persisted.getTargetId()).isEqualTo(targetId);
        assertThat(persisted.getResult()).isEqualTo(AuditResultEnum.SUCCESS);
        assertThat(persisted.getDetailsJson()).isEqualTo(details);
        assertThat(persisted.getCreatedAt()).isNotNull();
    }

    @Test
    public void givenPersistedAuditLogs_whenGetAll_thenReturnMappedDtos() {
        User actor = createUser("manager");
        AuditLog auditLog = new AuditLog();
        auditLog.setId(UUID.randomUUID());
        auditLog.setActorUser(actor);
        auditLog.setActorSource(ActorSourceEnum.WEB);
        auditLog.setActionType("UPDATE_DEVICE");
        auditLog.setTargetType("DEVICE");
        auditLog.setTargetId(UUID.randomUUID());
        auditLog.setResult(AuditResultEnum.SUCCESS);
        auditLog.setCreatedAt(LocalDateTime.now());
        auditLog.setDetailsJson(Map.of("hostname", "pc-02"));
        when(auditLogRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(auditLog));

        List<AuditLogDto> result = auditService.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().actionType()).isEqualTo("UPDATE_DEVICE");
        assertThat(result.getFirst().actorUserId()).isEqualTo(actor.getId());
    }

    private User createUser(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAdUsername(username);
        user.setDisplayName(username);
        return user;
    }
}
