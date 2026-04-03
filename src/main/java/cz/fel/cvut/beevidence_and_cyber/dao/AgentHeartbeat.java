package cz.fel.cvut.beevidence_and_cyber.dao;

import cz.fel.cvut.beevidence_and_cyber.enumeration.ServiceStatusEnum;
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
@Table(name = "agent_heartbeat")
public class AgentHeartbeat extends AbstractUuidEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private EndpointDevice device;

    private String agentVersion;

    @Enumerated(EnumType.STRING)
    private ServiceStatusEnum serviceStatus;

    private LocalDateTime lastSeenAt;

    private LocalDateTime lastCollectAt;

    private String lastError;
}
