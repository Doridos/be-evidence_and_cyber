package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UserDto(
        UUID id,
        String adUsername,
        String displayName,
        String email,
        String department,
        boolean enabled,
        LocalDateTime lastLoginAt,
        String source,
        List<RoleDto> roles
) {
}
