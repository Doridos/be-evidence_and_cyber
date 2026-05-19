package cz.fel.cvut.beevidence_and_cyber.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseIndexBootstrapService {

    private static final List<String> INDEX_STATEMENTS = List.of(
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_endpoint_device_hostname_lower ON endpoint_device (lower(hostname))",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_device_snapshot_device_version ON device_snapshot (device_id, version_no DESC)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_device_snapshot_device_collected_at ON device_snapshot (device_id, collected_at DESC)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_device_snapshot_collected_at ON device_snapshot (collected_at)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_network_interface_snapshot_id ON network_interface (snapshot_id)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_logged_in_session_snapshot_id ON logged_in_session (snapshot_id)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_agent_heartbeat_device_last_seen_at ON agent_heartbeat (device_id, last_seen_at DESC)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_agent_heartbeat_last_seen_at ON agent_heartbeat (last_seen_at)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_telemetry_sample_device_collected_at ON telemetry_sample (device_id, collected_at DESC)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_telemetry_sample_collected_at ON telemetry_sample (collected_at)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_device_log_entry_device_occurred_at ON device_log_entry (device_id, occurred_at DESC)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_device_log_entry_occurred_at ON device_log_entry (occurred_at)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_file_system_event_device_occurred_at ON file_system_event (device_id, occurred_at DESC)",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_file_system_event_occurred_at ON file_system_event (occurred_at)"
    );

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        for (String statement : INDEX_STATEMENTS) {
            try {
                jdbcTemplate.execute(statement);
            } catch (Exception exception) {
                log.warn("Index bootstrap skipped statement due to: {}", exception.getMessage());
                log.debug("Failed index statement: {}", statement, exception);
            }
        }
    }
}
