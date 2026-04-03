package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.Role;
import cz.fel.cvut.beevidence_and_cyber.dao.RolePermissionAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RolePermissionAssignmentRepository extends JpaRepository<RolePermissionAssignment, UUID> {
    List<RolePermissionAssignment> findByRole(Role role);
    void deleteByRole(Role role);
}
