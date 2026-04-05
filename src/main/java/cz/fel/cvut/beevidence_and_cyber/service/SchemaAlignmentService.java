package cz.fel.cvut.beevidence_and_cyber.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaAlignmentService implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        alignRemoteSessionHelpRequestColumn();
    }

    private void alignRemoteSessionHelpRequestColumn() {
        try {
            jdbcTemplate.execute("alter table remote_session alter column help_request_id drop not null");
            log.info("Database schema alignment applied: remote_session.help_request_id is now nullable.");
        } catch (Exception exception) {
            log.warn("Database schema alignment for remote_session.help_request_id could not be applied automatically: {}",
                    exception.getMessage());
        }
    }
}
