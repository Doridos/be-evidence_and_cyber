package cz.fel.cvut.beevidence_and_cyber.dao;

import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_log")
public class AuditLog extends AbstractUuidEntity {

    @ManyToOne
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @Enumerated(EnumType.STRING)
    private ActorSourceEnum actorSource;

    private String actionType;

    private String targetType;

    private UUID targetId;

    @Enumerated(EnumType.STRING)
    private AuditResultEnum result;

    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> detailsJson;
}
