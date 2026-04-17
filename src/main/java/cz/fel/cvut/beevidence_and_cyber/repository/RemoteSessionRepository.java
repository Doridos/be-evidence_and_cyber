package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.RemoteSession;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RemoteSessionRepository extends JpaRepository<RemoteSession, UUID> {
    List<RemoteSession> findAllByOrderByStartedAtDesc();
    List<RemoteSession> findByDeviceOrderByStartedAtDesc(EndpointDevice device);
}
