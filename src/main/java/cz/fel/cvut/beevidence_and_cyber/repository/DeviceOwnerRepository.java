package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.DeviceOwner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceOwnerRepository extends JpaRepository<DeviceOwner, UUID> {

    List<DeviceOwner> findAllByOrderByLastNameAscFirstNameAsc();

    Optional<DeviceOwner> findByFirstNameIgnoreCaseAndLastNameIgnoreCase(String firstName, String lastName);
}
