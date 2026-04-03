package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.Permission;
import cz.fel.cvut.beevidence_and_cyber.dao.Role;
import cz.fel.cvut.beevidence_and_cyber.dao.RolePermissionAssignment;
import cz.fel.cvut.beevidence_and_cyber.repository.PermissionRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.RolePermissionAssignmentRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class BootstrapDataService implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionAssignmentRepository rolePermissionAssignmentRepository;

    @Override
    public void run(String... args) {
        Role manager = ensureRole("MANAGER", "Manager", "Can view system data", true);
        Role admin = ensureRole("ADMIN", "Admin", "Can manage the entire system", true);

        Permission viewDevices = ensurePermission("VIEW_DEVICES", "View devices", "View endpoint inventory.");
        Permission manageDevices = ensurePermission("MANAGE_DEVICES", "Manage devices", "Create and update endpoint devices.");
        Permission viewDetections = ensurePermission("VIEW_DETECTIONS", "View detections", "View detections and analysis runs.");
        Permission manageDetections = ensurePermission("MANAGE_DETECTIONS", "Manage detections", "Manage rules and findings.");
        Permission manageRemoteHelp = ensurePermission("MANAGE_REMOTE_HELP", "Manage remote help", "Manage help requests and sessions.");
        Permission manageCommands = ensurePermission("MANAGE_COMMANDS", "Manage commands", "Create command requests and executions.");
        Permission manageRoles = ensurePermission("MANAGE_ROLES", "Manage roles", "Assign roles and permissions.");

        assignPermissions(manager, Set.of(viewDevices, viewDetections));
        assignPermissions(admin, Set.of(viewDevices, manageDevices, viewDetections, manageDetections, manageRemoteHelp, manageCommands, manageRoles));
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

    private Permission ensurePermission(String code, String name, String description) {
        return permissionRepository.findByCodeIgnoreCase(code).orElseGet(() -> {
            Permission permission = new Permission();
            permission.setCode(code);
            permission.setName(name);
            permission.setDescription(description);
            return permissionRepository.save(permission);
        });
    }

    private void assignPermissions(Role role, Set<Permission> permissions) {
        List<RolePermissionAssignment> existingAssignments = rolePermissionAssignmentRepository.findByRole(role);
        if (!existingAssignments.isEmpty()) {
            return;
        }

        for (Permission permission : permissions) {
            RolePermissionAssignment assignment = new RolePermissionAssignment();
            assignment.setRole(role);
            assignment.setPermission(permission);
            assignment.setAssignedAt(LocalDateTime.now());
            rolePermissionAssignmentRepository.save(assignment);
        }
    }
}
