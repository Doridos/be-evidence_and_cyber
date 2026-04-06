package cz.fel.cvut.beevidence_and_cyber.dao;

import cz.fel.cvut.beevidence_and_cyber.enumeration.FindingStatusEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.SeverityLevelEnum;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "detection_finding")
public class DetectionFinding extends AbstractUuidEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private EndpointDevice device;

    @ManyToOne
    @JoinColumn(name = "rule_id")
    private DetectionRule rule;

    @Enumerated(EnumType.STRING)
    private FindingStatusEnum status;

    @Enumerated(EnumType.STRING)
    private SeverityLevelEnum severity;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime firstSeenAt;

    private LocalDateTime lastSeenAt;

    private boolean createdByAi;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> contextJson;
}
