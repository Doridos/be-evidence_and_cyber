package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotBlank;

public record ControlApprovalRequest(
        @NotBlank String decision,
        String decidedByUsername,
        String note
) {
}
