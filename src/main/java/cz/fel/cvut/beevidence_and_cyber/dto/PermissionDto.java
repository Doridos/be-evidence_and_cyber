package cz.fel.cvut.beevidence_and_cyber.dto;

import java.util.UUID;

public record PermissionDto(
        UUID id,
        String code,
        String name,
        String description
) {
}
