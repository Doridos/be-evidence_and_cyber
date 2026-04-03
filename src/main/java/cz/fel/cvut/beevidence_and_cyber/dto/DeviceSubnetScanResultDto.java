package cz.fel.cvut.beevidence_and_cyber.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DeviceSubnetScanResultDto(
        String subnetCidr,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long durationMs,
        int scannedHosts,
        int activeHosts,
        List<DiscoveredDeviceDto> devices
) {
}
