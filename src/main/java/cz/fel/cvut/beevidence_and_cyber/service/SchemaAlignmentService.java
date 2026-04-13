package cz.fel.cvut.beevidence_and_cyber.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cz.fel.cvut.beevidence_and_cyber.dao.DeviceOwner;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceOwnerRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.EndpointDeviceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaAlignmentService implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DeviceOwnerRepository deviceOwnerRepository;
    private final EndpointDeviceRepository endpointDeviceRepository;

    @Override
    public void run(String... args) {
        alignRemoteSessionHelpRequestColumn();
        alignDeviceLogEntryLogSourceConstraint();
        alignCommandExecutionResultJsonColumn();
        alignCommandRequestCommandTypeConstraint();
        alignEndpointDeviceUsbRemovableBlockedColumn();
        alignEndpointDeviceOwnerColumns();
        alignDetectionFindingEventTable();
        migrateLegacyDeviceOwners();
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

    private void alignCommandExecutionResultJsonColumn() {
        try {
            jdbcTemplate.execute("alter table command_execution add column if not exists result_json jsonb");
            log.info("Database schema alignment applied: command_execution.result_json is available.");
        } catch (Exception exception) {
            log.warn("Database schema alignment for command_execution.result_json could not be applied automatically: {}",
                    exception.getMessage());
        }
    }

    private void alignCommandRequestCommandTypeConstraint() {
        try {
            jdbcTemplate.execute("alter table command_request drop constraint if exists command_request_command_type_check");
            jdbcTemplate.execute(
                    "alter table command_request add constraint command_request_command_type_check " +
                            "check (command_type in ('PRINTER','NETWORK_DRIVE','SERVICE','PROCESS','USB','COLLECT'))"
            );
            log.info("Database schema alignment applied: command_request.command_type now allows PROCESS.");
        } catch (Exception exception) {
            log.warn("Database schema alignment for command_request.command_type could not be applied automatically: {}",
                    exception.getMessage());
        }
    }

    private void alignEndpointDeviceUsbRemovableBlockedColumn() {
        try {
            jdbcTemplate.execute("alter table endpoint_device add column if not exists usb_removable_blocked boolean not null default false");
            jdbcTemplate.execute("update endpoint_device set usb_removable_blocked = false where usb_removable_blocked is null");
            log.info("Database schema alignment applied: endpoint_device.usb_removable_blocked is available.");
        } catch (Exception exception) {
            log.warn("Database schema alignment for endpoint_device.usb_removable_blocked could not be applied automatically: {}",
                    exception.getMessage());
        }
    }

    private void alignEndpointDeviceOwnerColumns() {
        try {
            jdbcTemplate.execute("alter table endpoint_device add column if not exists owner_id uuid");
            jdbcTemplate.execute("alter table endpoint_device add column if not exists owner_first_name varchar(255)");
            jdbcTemplate.execute("alter table endpoint_device add column if not exists owner_last_name varchar(255)");
            log.info("Database schema alignment applied: endpoint_device owner columns are available.");
        } catch (Exception exception) {
            log.warn("Database schema alignment for endpoint_device owner columns could not be applied automatically: {}",
                    exception.getMessage());
        }
    }

    private void alignDetectionFindingEventTable() {
        try {
            jdbcTemplate.execute(
                    "create table if not exists detection_finding_event (" +
                            "id uuid not null primary key, " +
                            "finding_id uuid not null references detection_finding(id), " +
                            "event_type varchar(255) not null, " +
                            "occurred_at timestamp(6), " +
                            "source_record_id uuid, " +
                            "source_log varchar(255), " +
                            "\"level\" varchar(255), " +
                            "event_code varchar(255), " +
                            "message text, " +
                            "path text, " +
                            "actor_username varchar(255), " +
                            "payload_json jsonb, " +
                            "constraint detection_finding_event_event_type_check check (event_type in ('LOG','FILE','SNAPSHOT'))" +
                            ")"
            );
            jdbcTemplate.execute("create index if not exists idx_detection_finding_event_finding_id on detection_finding_event(finding_id)");
            jdbcTemplate.execute("create index if not exists idx_detection_finding_event_occurred_at on detection_finding_event(occurred_at)");
            log.info("Database schema alignment applied: detection_finding_event table is available.");
        } catch (Exception exception) {
            log.warn("Database schema alignment for detection_finding_event could not be applied automatically: {}",
                    exception.getMessage());
        }
    }

    private void migrateLegacyDeviceOwners() {
        try {
            Map<String, DeviceOwner> ownerIndex = new LinkedHashMap<>();
            for (DeviceOwner owner : deviceOwnerRepository.findAll()) {
                ownerIndex.put((owner.getFirstName() + "|" + owner.getLastName()).toLowerCase(), owner);
            }

            boolean changed = false;
            for (EndpointDevice device : endpointDeviceRepository.findAll()) {
                if (device.getOwner() != null) {
                    continue;
                }
                String firstName = normalize(device.getOwnerFirstName());
                String lastName = normalize(device.getOwnerLastName());
                if (firstName == null || lastName == null) {
                    continue;
                }
                String key = (firstName + "|" + lastName).toLowerCase();
                DeviceOwner owner = ownerIndex.get(key);
                if (owner == null) {
                    owner = new DeviceOwner();
                    owner.setFirstName(firstName);
                    owner.setLastName(lastName);
                    owner = deviceOwnerRepository.save(owner);
                    ownerIndex.put(key, owner);
                }
                device.setOwner(owner);
                device.setOwnerFirstName(owner.getFirstName());
                device.setOwnerLastName(owner.getLastName());
                endpointDeviceRepository.save(device);
                changed = true;
            }

            if (changed) {
                log.info("Database schema alignment applied: legacy device owners were migrated to device_owner.");
            }
        } catch (Exception exception) {
            log.warn("Database schema alignment for legacy device owners could not be applied automatically: {}",
                    exception.getMessage());
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
