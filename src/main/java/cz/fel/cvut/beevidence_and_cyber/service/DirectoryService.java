package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.*;
import cz.fel.cvut.beevidence_and_cyber.dto.RoleDto;
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
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ApiMapper apiMapper;
    private final AuditService auditService;

    private static final String DEFAULT_ROLE_CODE = "USER";

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
    public User ensureUserExists(String username, String displayName) {
        return userRepository.findByAdUsernameIgnoreCase(username)
                .map(existing -> {
                    if (displayName != null && !displayName.isBlank()) {
                        existing.setDisplayName(displayName);
                    }
                    if (existing.getDisplayName() == null || existing.getDisplayName().isBlank()) {
                        existing.setDisplayName(username);
                    }
                    User savedUser = userRepository.save(existing);
                    ensureDefaultRoleAssigned(savedUser);
                    return savedUser;
                })
                .orElseGet(() -> {
                    User user = new User();
                    user.setAdUsername(username);
                    user.setDisplayName(displayName == null || displayName.isBlank() ? username : displayName);
                    user.setEnabled(true);
                    user.setSource("AD");
                    User savedUser = userRepository.save(user);
                    ensureDefaultRoleAssigned(savedUser);
                    return savedUser;
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
        return apiMapper.toDto(role);
    }

    private void ensureDefaultRoleAssigned(User user) {
        boolean alreadyHasRole = userRoleAssignmentRepository.findByUser(user).stream()
                .anyMatch(assignment -> assignment.getRole() != null);

        if (alreadyHasRole) {
            return;
        }

        Role defaultRole = roleRepository.findByCodeIgnoreCase(DEFAULT_ROLE_CODE)
                .orElseThrow(() -> new NotFoundException("Default role USER is not initialized"));

        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setUser(user);
        assignment.setRole(defaultRole);
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setValidFrom(LocalDateTime.now());
        userRoleAssignmentRepository.save(assignment);
    }
}
