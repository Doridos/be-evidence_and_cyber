package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.DeviceDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceDepartmentRepository extends JpaRepository<DeviceDepartment, UUID> {

    List<DeviceDepartment> findAllByOrderByNameAsc();

    Optional<DeviceDepartment> findByNameIgnoreCase(String name);
}
