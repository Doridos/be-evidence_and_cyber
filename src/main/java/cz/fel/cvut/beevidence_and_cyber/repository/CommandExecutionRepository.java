package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.CommandExecution;
import cz.fel.cvut.beevidence_and_cyber.dao.CommandRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommandExecutionRepository extends JpaRepository<CommandExecution, UUID> {
    List<CommandExecution> findByCommandRequest(CommandRequest commandRequest);
    CommandExecution findTopByCommandRequestOrderByStartedAtDescIdDesc(CommandRequest commandRequest);
}
