package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dao.UserRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UUID> {
    List<UserRoleAssignment> findByUser(User user);
    void deleteByUser(User user);
}
