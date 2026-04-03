package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.DeviceSnapshot;
import cz.fel.cvut.beevidence_and_cyber.dao.LoggedInSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoggedInSessionRepository extends JpaRepository<LoggedInSession, UUID> {
    List<LoggedInSession> findBySnapshot(DeviceSnapshot snapshot);
    void deleteBySnapshot(DeviceSnapshot snapshot);
}
