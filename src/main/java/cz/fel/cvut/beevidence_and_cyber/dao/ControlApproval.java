package cz.fel.cvut.beevidence_and_cyber.dao;

import cz.fel.cvut.beevidence_and_cyber.enumeration.ApprovalDecisionEnum;
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

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "control_approval")
public class ControlApproval extends AbstractUuidEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "remote_session_id", nullable = false)
    private RemoteSession remoteSession;

    private LocalDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    private ApprovalDecisionEnum decision;

    private String decidedByUsername;

    private LocalDateTime decidedAt;

    @Column(columnDefinition = "TEXT")
    private String note;
}
