package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.OffsetDateTime;

public record AgentLoggedInSessionPayload(
        String username,
        String domain,
        String sessionType,
        String state,
        OffsetDateTime loginTime
) {
}
