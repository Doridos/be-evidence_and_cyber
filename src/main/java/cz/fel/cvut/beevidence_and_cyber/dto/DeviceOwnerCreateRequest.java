package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceOwnerCreateRequest(
        @NotBlank String firstName,
        @NotBlank String lastName
) {
}
