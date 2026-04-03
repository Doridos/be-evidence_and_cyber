package cz.fel.cvut.beevidence_and_cyber.dto;

import java.util.List;
import java.util.UUID;

public record RoleDto(
        UUID id,
        String code,
        String name,
        String description,
        boolean system,
        List<PermissionDto> permissions
) {
}
