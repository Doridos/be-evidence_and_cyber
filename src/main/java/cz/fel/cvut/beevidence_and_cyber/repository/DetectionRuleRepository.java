package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.DetectionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DetectionRuleRepository extends JpaRepository<DetectionRule, UUID> {
    Optional<DetectionRule> findByCodeIgnoreCase(String code);
}
