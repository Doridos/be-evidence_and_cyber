package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.DetectionRule;
import cz.fel.cvut.beevidence_and_cyber.dao.Role;
import cz.fel.cvut.beevidence_and_cyber.enumeration.DetectionSourceTypeEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.SeverityLevelEnum;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionRuleRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BootstrapDataService implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final DetectionRuleRepository detectionRuleRepository;

    @Override
    public void run(String... args) {
        ensureRole("USER", "User", "Default application role for authenticated users", true);
        ensureRole("MANAGER", "Manager", "Can view system data", true);
        ensureRole("ADMIN", "Admin", "Can manage the entire system", true);
        ensureDefaultDetectionRules();
    }

    private Role ensureRole(String code, String name, String description, boolean system) {
        return roleRepository.findByCodeIgnoreCase(code).orElseGet(() -> {
            Role role = new Role();
            role.setCode(code);
            role.setName(name);
            role.setDescription(description);
            role.setSystem(system);
            return roleRepository.save(role);
        });
    }

    private void ensureDefaultDetectionRules() {
        ensureDetectionRule(
                "TASK_FILE_CHANGED",
                "Změna naplánované úlohy",
                "Detekuje vytvoření, úpravu nebo smazání souborů reprezentujících naplánované úlohy Windows.",
                SeverityLevelEnum.HIGH,
                DetectionSourceTypeEnum.FILE,
                Map.of(
                        "kind", "file_path_prefix",
                        "pathPrefixes", List.of("\\windows\\system32\\tasks", "\\windows\\tasks")
                )
        );
        ensureDetectionRule(
                "LOCAL_USER_CREATED",
                "Přidání lokálního uživatele",
                "Detekuje vytvoření nového lokálního nebo doménového účtu na zařízení podle bezpečnostního logu.",
                SeverityLevelEnum.HIGH,
                DetectionSourceTypeEnum.LOG,
                Map.of("kind", "event_code", "eventCodes", List.of("4720"))
        );
        ensureDetectionRule(
                "LOCAL_USER_DELETED",
                "Smazání lokálního uživatele",
                "Detekuje smazání lokálního nebo doménového účtu na zařízení podle bezpečnostního logu.",
                SeverityLevelEnum.HIGH,
                DetectionSourceTypeEnum.LOG,
                Map.of("kind", "event_code", "eventCodes", List.of("4726"))
        );
        ensureDetectionRule(
                "MONITORED_PATH_CHANGED",
                "Změna v monitorované cestě",
                "Detekuje libovolnou změnu souboru v monitorovaných citlivých systémových cestách.",
                SeverityLevelEnum.MEDIUM,
                DetectionSourceTypeEnum.FILE,
                Map.of("kind", "generic_monitored_path_change")
        );
        ensureDetectionRule(
                "NETWORK_INTERFACE_ADDED",
                "Přidání síťového rozhraní",
                "Detekuje nově přidané síťové rozhraní mezi dvěma snapshoty zařízení.",
                SeverityLevelEnum.MEDIUM,
                DetectionSourceTypeEnum.TELEMETRY,
                Map.of("kind", "snapshot_network_interface_added")
        );
        ensureDetectionRule(
                "NETWORK_INTERFACE_REMOVED",
                "Odebrání síťového rozhraní",
                "Detekuje odebrané síťové rozhraní mezi dvěma snapshoty zařízení.",
                SeverityLevelEnum.MEDIUM,
                DetectionSourceTypeEnum.TELEMETRY,
                Map.of("kind", "snapshot_network_interface_removed")
        );
        ensureDetectionRule(
                "OS_UPDATED",
                "Aktualizace operačního systému",
                "Detekuje změnu názvu, verze nebo buildu operačního systému mezi dvěma snapshoty zařízení.",
                SeverityLevelEnum.HIGH,
                DetectionSourceTypeEnum.TELEMETRY,
                Map.of("kind", "snapshot_os_changed")
        );
        ensureDetectionRule(
                "ELEVATED_POWERSHELL_PROCESS",
                "Spuštění PowerShellu s elevovanými právy",
                "Detekuje spuštění powershell.exe nebo pwsh.exe se zvýšenými oprávněními.",
                SeverityLevelEnum.HIGH,
                DetectionSourceTypeEnum.LOG,
                Map.of("kind", "elevated_process", "processNames", List.of("powershell.exe", "pwsh.exe"))
        );
        ensureDetectionRule(
                "FAILED_LOGON_BURST",
                "Opakované neúspěšné přihlášení",
                "Detekuje sérii neúspěšných pokusů o přihlášení ve krátkém časovém okně.",
                SeverityLevelEnum.HIGH,
                DetectionSourceTypeEnum.LOG,
                Map.of("kind", "failed_logon_burst", "eventCode", "4625", "threshold", 5, "windowMinutes", 10)
        );
        ensureDetectionRule(
                "RDP_LOGON",
                "Nové RDP přihlášení",
                "Detekuje úspěšné vzdálené přihlášení typu RemoteInteractive.",
                SeverityLevelEnum.MEDIUM,
                DetectionSourceTypeEnum.LOG,
                Map.of("kind", "rdp_logon", "eventCode", "4624", "logonType", "10")
        );
        ensureDetectionRule(
                "SERVICE_INSTALLED",
                "Instalace nové služby",
                "Detekuje instalaci nebo registraci nové služby ve Windows přes Security i System logy.",
                SeverityLevelEnum.HIGH,
                DetectionSourceTypeEnum.LOG,
                Map.of("kind", "event_code", "eventCodes", List.of("4697", "7045"))
        );
        ensureDetectionRule(
                "USB_DEVICE_CONNECTED",
                "Připojení USB zařízení",
                "Detekuje připojení nebo inicializaci USB zařízení podle systémových PnP událostí.",
                SeverityLevelEnum.MEDIUM,
                DetectionSourceTypeEnum.LOG,
                Map.of("kind", "usb_system_event", "eventCodes", List.of("400", "410", "430", "20001"))
        );
        ensureDetectionRule(
                "HOSTS_FILE_CHANGED",
                "Změna souboru hosts",
                "Detekuje změnu systémového souboru hosts.",
                SeverityLevelEnum.HIGH,
                DetectionSourceTypeEnum.FILE,
                Map.of("kind", "file_path_suffix", "pathSuffixes", List.of("\\windows\\system32\\drivers\\etc\\hosts"))
        );
        ensureDetectionRule(
                "STARTUP_PERSISTENCE_CHANGED",
                "Změna v Startup složce",
                "Detekuje vytvoření nebo úpravu položek ve veřejné Startup složce.",
                SeverityLevelEnum.HIGH,
                DetectionSourceTypeEnum.FILE,
                Map.of("kind", "file_path_contains", "pathFragments", List.of("\\programdata\\microsoft\\windows\\start menu\\programs\\startup"))
        );
    }

    private void ensureDetectionRule(String code,
                                     String name,
                                     String description,
                                     SeverityLevelEnum severity,
                                     DetectionSourceTypeEnum sourceType,
                                     Map<String, Object> conditionJson) {
        detectionRuleRepository.findByCodeIgnoreCase(code).orElseGet(() -> {
            DetectionRule rule = new DetectionRule();
            rule.setCode(code);
            rule.setName(name);
            rule.setDescription(description);
            rule.setSeverity(severity);
            rule.setSourceType(sourceType);
            rule.setConditionJson(conditionJson);
            rule.setEnabled(true);
            return detectionRuleRepository.save(rule);
        });
    }
}
