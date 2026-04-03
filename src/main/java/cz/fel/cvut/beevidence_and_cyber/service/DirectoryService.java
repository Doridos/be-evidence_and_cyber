package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.*;
import cz.fel.cvut.beevidence_and_cyber.dto.PermissionDto;
import cz.fel.cvut.beevidence_and_cyber.dto.RoleDto;
import cz.fel.cvut.beevidence_and_cyber.dto.RolePermissionAssignmentRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.UserDto;
import cz.fel.cvut.beevidence_and_cyber.dto.UserRoleAssignmentRequest;
import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import cz.fel.cvut.beevidence_and_cyber.exception.NotFoundException;
import cz.fel.cvut.beevidence_and_cyber.repository.*;
import cz.fel.cvut.beevidence_and_cyber.security.ApplicationUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DirectoryService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RolePermissionAssignmentRepository rolePermissionAssignmentRepository;
    private final ApiMapper apiMapper;
    private final AuditService auditService;

    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream().map(this::toUserDto).toList();
    }

    public UserDto getUser(UUID id) {
        return toUserDto(findUser(id));
    }

    public User findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User with id " + id + " not found"));
    }

    public User findUserByUsername(String username) {
        return userRepository.findByAdUsernameIgnoreCase(username)
                .orElseThrow(() -> new NotFoundException("User with username " + username + " not found"));
    }

    public List<RoleDto> getAllRoles() {
        return roleRepository.findAll().stream().map(this::toRoleDto).toList();
    }

    public List<PermissionDto> getAllPermissions() {
        return permissionRepository.findAll().stream().map(apiMapper::toDto).toList();
    }

    @Transactional
    public UserDto assignRoles(UUID userId, UserRoleAssignmentRequest request, User actor) {
        User user = findUser(userId);
        userRoleAssignmentRepository.deleteByUser(user);
        List<Role> roles = roleRepository.findAllById(request.roleIds());
        for (Role role : roles) {
            UserRoleAssignment assignment = new UserRoleAssignment();
            assignment.setUser(user);
            assignment.setRole(role);
            assignment.setAssignedBy(actor);
            assignment.setAssignedAt(LocalDateTime.now());
            assignment.setValidFrom(LocalDateTime.now());
            userRoleAssignmentRepository.save(assignment);
        }
        auditService.log(actor, ActorSourceEnum.WEB, "ASSIGN_USER_ROLES", "USER", user.getId(), AuditResultEnum.SUCCESS,
                Map.of("roleIds", request.roleIds()));
        return toUserDto(user);
    }

    @Transactional
    public RoleDto assignPermissions(UUID roleId, RolePermissionAssignmentRequest request, User actor) {
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new NotFoundException("Role with id " + roleId + " not found"));
        rolePermissionAssignmentRepository.deleteByRole(role);
        List<Permission> permissions = permissionRepository.findAllById(request.permissionIds());
        for (Permission permission : permissions) {
            RolePermissionAssignment assignment = new RolePermissionAssignment();
            assignment.setRole(role);
            assignment.setPermission(permission);
            assignment.setAssignedAt(LocalDateTime.now());
            rolePermissionAssignmentRepository.save(assignment);
        }
        auditService.log(actor, ActorSourceEnum.WEB, "ASSIGN_ROLE_PERMISSIONS", "ROLE", role.getId(), AuditResultEnum.SUCCESS,
                Map.of("permissionIds", request.permissionIds()));
        return toRoleDto(role);
    }

    @Transactional
    public User ensureUserExists(String username, String displayName) {
        return userRepository.findByAdUsernameIgnoreCase(username)
                .map(existing -> {
                    if (displayName != null && !displayName.isBlank()) {
                        existing.setDisplayName(displayName);
                    }
                    if (existing.getDisplayName() == null || existing.getDisplayName().isBlank()) {
                        existing.setDisplayName(username);
                    }
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User user = new User();
                    user.setAdUsername(username);
                    user.setDisplayName(displayName == null || displayName.isBlank() ? username : displayName);
                    user.setEnabled(true);
                    user.setSource("AD");
                    return userRepository.save(user);
                });
    }

    @Transactional
    public void updateLastLogin(User user) {
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public ApplicationUserPrincipal loadPrincipalByUsername(String username) {
        User user = findUserByUsername(username);
        List<GrantedAuthority> authorities = userRoleAssignmentRepository.findByUser(user).stream()
                .map(UserRoleAssignment::getRole)
                .map(Role::getCode)
                .distinct()
                .map(roleCode -> new SimpleGrantedAuthority("ROLE_" + roleCode.toUpperCase(Locale.ROOT)))
                .map(GrantedAuthority.class::cast)
                .toList();
        return new ApplicationUserPrincipal(user, authorities);
    }

    public UserDto toUserDto(User user) {
        List<RoleDto> roles = userRoleAssignmentRepository.findByUser(user).stream()
                .map(UserRoleAssignment::getRole)
                .distinct()
                .map(this::toRoleDto)
                .toList();
        return apiMapper.toDto(user, roles);
    }

    public RoleDto toRoleDto(Role role) {
        List<PermissionDto> permissions = rolePermissionAssignmentRepository.findByRole(role).stream()
                .map(RolePermissionAssignment::getPermission)
                .distinct()
                .map(apiMapper::toDto)
                .toList();
        return apiMapper.toDto(role, permissions);
    }
}
