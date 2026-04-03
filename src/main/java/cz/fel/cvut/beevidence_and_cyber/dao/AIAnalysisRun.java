package cz.fel.cvut.beevidence_and_cyber.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_analysis_run")
public class AIAnalysisRun extends AbstractUuidEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private EndpointDevice device;

    @ManyToOne
    @JoinColumn(name = "triggered_by_user_id")
    private User triggeredByUser;

    private String modelName;

    private String promptVersion;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String resultSummary;

    private BigDecimal riskScore;
}
