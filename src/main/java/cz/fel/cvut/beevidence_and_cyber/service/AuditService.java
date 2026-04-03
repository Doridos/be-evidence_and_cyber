package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.AuditLog;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.AuditLogDto;
import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import cz.fel.cvut.beevidence_and_cyber.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ApiMapper apiMapper;

    public AuditLog log(User actorUser,
                        ActorSourceEnum actorSource,
                        String actionType,
                        String targetType,
                        UUID targetId,
                        AuditResultEnum result,
                        Map<String, Object> detailsJson) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUser(actorUser);
        auditLog.setActorSource(actorSource);
        auditLog.setActionType(actionType);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setResult(result);
        auditLog.setCreatedAt(LocalDateTime.now());
        auditLog.setDetailsJson(detailsJson);
        return auditLogRepository.save(auditLog);
    }

    public List<AuditLogDto> getAll() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc().stream().map(apiMapper::toDto).toList();
    }
}
