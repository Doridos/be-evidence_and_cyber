package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCodeIgnoreCase(String code);
}
