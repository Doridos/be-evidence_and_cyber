package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.AgentHeartbeat;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentHeartbeatRepository extends JpaRepository<AgentHeartbeat, UUID> {
    List<AgentHeartbeat> findByDeviceOrderByLastSeenAtDesc(EndpointDevice device);
    Optional<AgentHeartbeat> findTopByDeviceOrderByLastSeenAtDesc(EndpointDevice device);
    long deleteByLastSeenAtBefore(LocalDateTime cutoff);
}
