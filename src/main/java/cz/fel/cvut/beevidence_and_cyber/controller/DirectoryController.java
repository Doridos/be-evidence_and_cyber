package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dto.RolePermissionAssignmentRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.UserRoleAssignmentRequest;
import cz.fel.cvut.beevidence_and_cyber.service.CurrentUserService;
import cz.fel.cvut.beevidence_and_cyber.service.DirectoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DirectoryController {

    private final DirectoryService directoryService;
    private final CurrentUserService currentUserService;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getAllUsers() {
        return directoryService.getAllUsers();
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public Object getUser(@PathVariable UUID id) {
        return directoryService.getUser(id);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public List<?> getAllRoles() {
        return directoryService.getAllRoles();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public List<?> getAllPermissions() {
        return directoryService.getAllPermissions();
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public Object assignRoles(@PathVariable UUID id, @Valid @RequestBody UserRoleAssignmentRequest request) {
        return directoryService.assignRoles(id, request, currentUserService.requireCurrentUser());
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public Object assignPermissions(@PathVariable UUID id, @Valid @RequestBody RolePermissionAssignmentRequest request) {
        return directoryService.assignPermissions(id, request, currentUserService.requireCurrentUser());
    }
}
