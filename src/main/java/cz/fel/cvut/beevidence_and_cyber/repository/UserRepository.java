package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByAdUsernameIgnoreCase(String adUsername);

    /**
     * Returns all enabled users that have the given role code assigned
     * and have a non-blank email address configured.
     */
    @Query("""
            SELECT u FROM User u
            JOIN u.roleAssignments ra
            WHERE ra.role.code = :roleCode
              AND u.enabled = true
              AND u.email IS NOT NULL
              AND u.email <> ''
            """)
    List<User> findEnabledUsersWithEmail(@Param("roleCode") String roleCode);
}
