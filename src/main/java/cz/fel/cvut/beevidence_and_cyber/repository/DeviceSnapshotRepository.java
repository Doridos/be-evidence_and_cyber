package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.DeviceSnapshot;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceSnapshotRepository extends JpaRepository<DeviceSnapshot, UUID> {
    List<DeviceSnapshot> findByDeviceOrderByCollectedAtDesc(EndpointDevice device);
    Optional<DeviceSnapshot> findTopByDeviceOrderByVersionNoDesc(EndpointDevice device);
    List<DeviceSnapshot> findByCollectedAtBefore(LocalDateTime cutoff);
}
