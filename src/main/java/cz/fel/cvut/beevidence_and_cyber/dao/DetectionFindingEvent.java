package cz.fel.cvut.beevidence_and_cyber.dao;

import cz.fel.cvut.beevidence_and_cyber.enumeration.DetectionFindingEventTypeEnum;
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
@Table(name = "detection_finding_event")
public class DetectionFindingEvent extends AbstractUuidEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "finding_id", nullable = false)
    private DetectionFinding finding;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DetectionFindingEventTypeEnum eventType;

    private LocalDateTime occurredAt;

    private UUID sourceRecordId;

    private String sourceLog;

    private String level;

    private String eventCode;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String path;

    private String actorUsername;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payloadJson;
}
