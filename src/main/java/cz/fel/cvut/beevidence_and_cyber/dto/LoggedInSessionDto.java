package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record LoggedInSessionDto(
        UUID id,
        String username,
        String domain,
        String sessionType,
        String state,
        LocalDateTime loginTime
) {
}
