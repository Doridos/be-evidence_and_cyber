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
        alignDeviceLogEntryLogSourceConstraint();
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

    private void alignDeviceLogEntryLogSourceConstraint() {
        try {
            jdbcTemplate.execute("alter table device_log_entry drop constraint if exists device_log_entry_log_source_check");
            jdbcTemplate.execute(
                    "alter table device_log_entry add constraint device_log_entry_log_source_check " +
                            "check (log_source in ('WINDOWS_SECURITY','WINDOWS_EVENT','AGENT','POWERSHELL'))"
            );
            log.info("Database schema alignment applied: device_log_entry.log_source now allows WINDOWS_SECURITY.");
        } catch (Exception exception) {
            log.warn("Database schema alignment for device_log_entry.log_source could not be applied automatically: {}",
                    exception.getMessage());
        }
    }
}
