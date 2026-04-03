package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.DeviceLogEntry;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeviceLogEntryRepository extends JpaRepository<DeviceLogEntry, UUID> {
    List<DeviceLogEntry> findByDeviceOrderByOccurredAtDesc(EndpointDevice device);
}
