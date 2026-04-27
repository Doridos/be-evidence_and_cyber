package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.RetentionSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetentionSettingsRepository extends JpaRepository<RetentionSettings, Integer> {
}
