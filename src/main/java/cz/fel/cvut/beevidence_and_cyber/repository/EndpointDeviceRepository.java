package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EndpointDeviceRepository extends JpaRepository<EndpointDevice, UUID> {
    Optional<EndpointDevice> findByHostnameIgnoreCase(String hostname);
    Optional<EndpointDevice> findByPrimaryIpIgnoreCase(String primaryIp);
    Optional<EndpointDevice> findByFqdnIgnoreCase(String fqdn);
}
