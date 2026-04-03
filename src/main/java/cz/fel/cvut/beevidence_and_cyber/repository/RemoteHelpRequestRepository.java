package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.RemoteHelpRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RemoteHelpRequestRepository extends JpaRepository<RemoteHelpRequest, UUID> {
    List<RemoteHelpRequest> findAllByOrderByRequestedAtDesc();
}
