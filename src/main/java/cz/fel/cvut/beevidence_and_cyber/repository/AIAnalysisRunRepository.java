package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.AIAnalysisRun;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AIAnalysisRunRepository extends JpaRepository<AIAnalysisRun, UUID> {
    List<AIAnalysisRun> findAllByOrderByStartedAtDesc();
    List<AIAnalysisRun> findByDeviceOrderByStartedAtDesc(EndpointDevice device);
    long deleteByStartedAtBefore(LocalDateTime cutoff);
}
