package cz.fel.cvut.beevidence_and_cyber.dto;

import java.util.UUID;

public record NetworkInterfaceDto(
        UUID id,
        String name,
        String displayName,
        String macAddress,
        String ipv4,
        String ipv6,
        boolean primary,
        boolean up
) {
}
