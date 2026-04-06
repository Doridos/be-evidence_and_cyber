package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.CommandRequest;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.enumeration.CommandStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommandRequestRepository extends JpaRepository<CommandRequest, UUID> {
    List<CommandRequest> findByDeviceOrderByCreatedAtDesc(EndpointDevice device);
    List<CommandRequest> findAllByOrderByCreatedAtDesc();
    List<CommandRequest> findByDeviceAndStatusOrderByCreatedAtAsc(EndpointDevice device, CommandStatusEnum status);
}
