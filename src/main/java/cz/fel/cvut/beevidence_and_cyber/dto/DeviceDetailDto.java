package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DeviceDetailDto(
        UUID id,
        String assetTag,
        String inventoryNumber,
        String hostname,
        String fqdn,
        String primaryIp,
        String site,
        UUID ownerId,
        String ownerFirstName,
        String ownerLastName,
        String status,
        boolean agentInstalled,
        boolean usbRemovableBlocked,
        LocalDateTime discoveredAt,
        LocalDateTime archivedAt,
        List<DeviceSnapshotDto> snapshots,
        List<AgentHeartbeatDto> heartbeats,
        List<TelemetrySampleDto> telemetrySamples,
        List<DeviceLogEntryDto> logEntries,
        List<FileSystemEventDto> fileSystemEvents
) {
}
