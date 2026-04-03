package cz.fel.cvut.beevidence_and_cyber.dao;

import cz.fel.cvut.beevidence_and_cyber.enumeration.DetectionSourceTypeEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.SeverityLevelEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "detection_rule")
public class DetectionRule extends AbstractUuidEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private SeverityLevelEnum severity;

    @Enumerated(EnumType.STRING)
    private DetectionSourceTypeEnum sourceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> conditionJson;

    private boolean enabled;
}
