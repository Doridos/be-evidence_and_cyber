package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.DeviceSnapshot;
import cz.fel.cvut.beevidence_and_cyber.dao.NetworkInterface;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NetworkInterfaceRepository extends JpaRepository<NetworkInterface, UUID> {
    List<NetworkInterface> findBySnapshot(DeviceSnapshot snapshot);
    void deleteBySnapshot(DeviceSnapshot snapshot);
}
