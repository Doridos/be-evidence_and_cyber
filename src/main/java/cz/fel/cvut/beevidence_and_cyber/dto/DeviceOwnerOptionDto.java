package cz.fel.cvut.beevidence_and_cyber.dto;

import java.util.UUID;

public record DeviceOwnerOptionDto(
        UUID id,
        String firstName,
        String lastName,
        String displayName
) {
}
