package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record RolePermissionAssignmentRequest(
        @NotEmpty List<UUID> permissionIds
) {
}
