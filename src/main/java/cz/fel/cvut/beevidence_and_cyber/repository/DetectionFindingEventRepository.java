package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.DetectionFinding;
import cz.fel.cvut.beevidence_and_cyber.dao.DetectionFindingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DetectionFindingEventRepository extends JpaRepository<DetectionFindingEvent, UUID> {

    List<DetectionFindingEvent> findByFindingOrderByOccurredAtAscIdAsc(DetectionFinding finding);

    boolean existsByFindingAndSourceRecordId(DetectionFinding finding, UUID sourceRecordId);
}
