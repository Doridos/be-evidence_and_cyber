package cz.fel.cvut.beevidence_and_cyber.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "command_execution")
public class CommandExecution extends AbstractUuidEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "command_request_id", nullable = false)
    private CommandRequest commandRequest;

    @ManyToOne
    @JoinColumn(name = "agent_heartbeat_id")
    private AgentHeartbeat agentHeartbeat;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Integer exitCode;

    @Column(columnDefinition = "TEXT")
    private String resultSummary;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
