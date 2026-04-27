package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.*;
import cz.fel.cvut.beevidence_and_cyber.dto.*;
import cz.fel.cvut.beevidence_and_cyber.enumeration.DeviceStatusEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import cz.fel.cvut.beevidence_and_cyber.exception.NotFoundException;
import cz.fel.cvut.beevidence_and_cyber.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final Duration HEARTBEAT_INACTIVITY_THRESHOLD = Duration.ofMinutes(5);

    private final EndpointDeviceRepository endpointDeviceRepository;
    private final DeviceOwnerRepository deviceOwnerRepository;
    private final DeviceSnapshotRepository deviceSnapshotRepository;
    private final NetworkInterfaceRepository networkInterfaceRepository;
    private final LoggedInSessionRepository loggedInSessionRepository;
    private final AgentHeartbeatRepository agentHeartbeatRepository;
    private final TelemetrySampleRepository telemetrySampleRepository;
    private final DeviceLogEntryRepository deviceLogEntryRepository;
    private final FileSystemEventRepository fileSystemEventRepository;
    private final SubnetScanService subnetScanService;
    private final AgentDeploymentService agentDeploymentService;
    private final ApiMapper apiMapper;
    private final AuditService auditService;

    public List<DeviceDetailDto> getAllDevices() {
        return endpointDeviceRepository.findAll().stream().map(this::toSummaryDto).toList();
    }

    public DeviceDetailDto getDevice(UUID id) {
        return toSummaryDto(findDevice(id));
    }

    public EndpointDevice findDevice(UUID id) {
        return endpointDeviceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Device with id " + id + " not found"));
    }

    public EndpointDevice findDeviceByHostname(String hostname) {
        return endpointDeviceRepository.findByHostnameIgnoreCase(hostname)
                .orElseThrow(() -> new NotFoundException("Device with hostname " + hostname + " not found"));
    }

    public List<DeviceOwnerOptionDto> getKnownOwners() {
        return deviceOwnerRepository.findAll().stream()
                .sorted(Comparator.comparing(DeviceOwner::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(DeviceOwner::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .map(owner -> new DeviceOwnerOptionDto(owner.getId(), owner.getFirstName(), owner.getLastName(),
                        owner.getFirstName() + " " + owner.getLastName()))
                .toList();
    }

    @Transactional
    public DeviceOwnerOptionDto createOwner(DeviceOwnerCreateRequest request, User actor) {
        String firstName = normalizeOwnerPart(request.firstName());
        String lastName = normalizeOwnerPart(request.lastName());
        if (firstName == null || lastName == null) {
            throw new IllegalArgumentException("Vlastník musí obsahovat jméno i příjmení.");
        }

        DeviceOwner owner = deviceOwnerRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCase(firstName, lastName)
                .orElseGet(() -> {
                    DeviceOwner created = new DeviceOwner();
                    created.setFirstName(firstName);
                    created.setLastName(lastName);
                    return deviceOwnerRepository.save(created);
                });

        auditService.log(actor, ActorSourceEnum.WEB, "CREATE_DEVICE_OWNER", "DEVICE_OWNER", owner.getId(), AuditResultEnum.SUCCESS,
                Map.of("firstName", owner.getFirstName(), "lastName", owner.getLastName()));
        return new DeviceOwnerOptionDto(owner.getId(), owner.getFirstName(), owner.getLastName(),
                owner.getFirstName() + " " + owner.getLastName());
    }

    @Transactional
    public DeviceDetailDto createDevice(DeviceCreateRequest request, User actor) {
        EndpointDevice device = new EndpointDevice();
        device.setAssetTag(request.assetTag());
        device.setInventoryNumber(request.inventoryNumber());
        device.setHostname(request.hostname());
        device.setFqdn(request.fqdn());
        device.setPrimaryIp(request.primaryIp());
        device.setSite(request.site());
        applyOwner(device, request.ownerId());
        device.setAgentInstalled(request.agentInstalled());
        device.setStatus(DeviceStatusEnum.ACTIVE);
        device.setDiscoveredAt(LocalDateTime.now());
        EndpointDevice saved = endpointDeviceRepository.save(device);

        auditService.log(actor, ActorSourceEnum.WEB, "CREATE_DEVICE", "DEVICE", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("hostname", saved.getHostname()));
        return toSummaryDto(saved);
    }

    @Transactional
    public DeviceDetailDto updateDevice(UUID id, DeviceUpdateRequest request, User actor) {
        EndpointDevice device = findDevice(id);
        if (request.assetTag() != null) {
            device.setAssetTag(request.assetTag());
        }
        if (request.inventoryNumber() != null) {
            device.setInventoryNumber(request.inventoryNumber());
        }
        if (request.fqdn() != null) {
            device.setFqdn(request.fqdn());
        }
        if (request.primaryIp() != null) {
            device.setPrimaryIp(request.primaryIp());
        }
        if (request.site() != null) {
            device.setSite(request.site());
        }
        if (request.ownerId() != null || device.getOwner() != null) {
            applyOwner(device, request.ownerId());
        }
        if (request.agentInstalled() != null) {
            device.setAgentInstalled(request.agentInstalled());
        }
        EndpointDevice saved = endpointDeviceRepository.save(device);
        auditService.log(actor, ActorSourceEnum.WEB, "UPDATE_DEVICE", "DEVICE", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("hostname", saved.getHostname()));
        return toSummaryDto(saved);
    }

    @Transactional
    public DeviceDetailDto archiveDevice(UUID id, User actor) {
        EndpointDevice device = findDevice(id);
        device.setStatus(DeviceStatusEnum.ARCHIVED);
        device.setArchivedAt(LocalDateTime.now());
        EndpointDevice saved = endpointDeviceRepository.save(device);
        auditService.log(actor, ActorSourceEnum.WEB, "ARCHIVE_DEVICE", "DEVICE", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("hostname", saved.getHostname()));
        return toSummaryDto(saved);
    }

    public List<DeviceSnapshotDto> getSnapshots(UUID deviceId) {
        return mapSnapshots(findDevice(deviceId));
    }

    public List<TelemetrySampleDto> getTelemetry(UUID deviceId) {
        return telemetrySampleRepository.findByDeviceOrderByCollectedAtDesc(findDevice(deviceId)).stream().map(apiMapper::toDto).toList();
    }

    public List<DeviceLogEntryDto> getLogs(UUID deviceId) {
        return deviceLogEntryRepository.findByDeviceOrderByOccurredAtDesc(findDevice(deviceId)).stream().map(apiMapper::toDto).toList();
    }

    public List<FileSystemEventDto> getFileSystemEvents(UUID deviceId) {
        return fileSystemEventRepository.findByDeviceOrderByOccurredAtDesc(findDevice(deviceId)).stream().map(apiMapper::toDto).toList();
    }

    public List<AgentHeartbeatDto> getHeartbeats(UUID deviceId) {
        return agentHeartbeatRepository.findByDeviceOrderByLastSeenAtDesc(findDevice(deviceId)).stream().map(apiMapper::toDto).toList();
    }

    public DeviceSubnetScanResultDto scanSubnet(DeviceSubnetScanRequest request, User actor) {
        return subnetScanService.scan(request, actor);
    }

    public AgentDeploymentResultDto deployAgent(UUID deviceId, AgentDeploymentRequest request, User actor) {
        return agentDeploymentService.deployAgent(findDevice(deviceId), request, actor);
    }

    public AgentDeploymentResultDto updateAgent(UUID deviceId, AgentDeploymentRequest request, User actor) {
        return agentDeploymentService.updateAgent(findDevice(deviceId), request, actor);
    }

    public AgentDeploymentResultDto uninstallAgent(UUID deviceId, AgentDeploymentRequest request, User actor) {
        return agentDeploymentService.uninstallAgent(findDevice(deviceId), request, actor);
    }

    public DeviceDetailDto toDetailDto(EndpointDevice device) {
        List<AgentHeartbeatDto> heartbeats = agentHeartbeatRepository.findByDeviceOrderByLastSeenAtDesc(device).stream().map(apiMapper::toDto).toList();
        return apiMapper.toDto(
                device,
                resolveEffectiveStatus(device, heartbeats),
                mapSnapshots(device),
                heartbeats,
                telemetrySampleRepository.findByDeviceOrderByCollectedAtDesc(device).stream().map(apiMapper::toDto).toList(),
                deviceLogEntryRepository.findByDeviceOrderByOccurredAtDesc(device).stream().map(apiMapper::toDto).toList(),
                fileSystemEventRepository.findByDeviceOrderByOccurredAtDesc(device).stream().map(apiMapper::toDto).toList()
        );
    }

    public DeviceDetailDto toSummaryDto(EndpointDevice device) {
        List<AgentHeartbeatDto> heartbeats = agentHeartbeatRepository.findByDeviceOrderByLastSeenAtDesc(device).stream().limit(1).map(apiMapper::toDto).toList();
        return apiMapper.toDto(
                device,
                resolveEffectiveStatus(device, heartbeats),
                List.of(),
                heartbeats,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private String resolveEffectiveStatus(EndpointDevice device, List<AgentHeartbeatDto> heartbeats) {
        if (device.getStatus() == DeviceStatusEnum.ARCHIVED) {
            return DeviceStatusEnum.ARCHIVED.name();
        }
        if (!device.isAgentInstalled()) {
            return device.getStatus().name();
        }
        LocalDateTime lastSeenAt = heartbeats.stream()
                .map(AgentHeartbeatDto::lastSeenAt)
                .findFirst()
                .orElse(null);
        if (lastSeenAt == null || lastSeenAt.isBefore(LocalDateTime.now().minus(HEARTBEAT_INACTIVITY_THRESHOLD))) {
            return DeviceStatusEnum.UNREACHABLE.name();
        }
        return DeviceStatusEnum.ACTIVE.name();
    }

    private List<DeviceSnapshotDto> mapSnapshots(EndpointDevice device) {
        return deviceSnapshotRepository.findByDeviceOrderByCollectedAtDesc(device).stream()
                .map(snapshot -> apiMapper.toDto(
                        snapshot,
                        networkInterfaceRepository.findBySnapshot(snapshot),
                        loggedInSessionRepository.findBySnapshot(snapshot)
                ))
                .toList();
    }

    private String normalizeOwnerPart(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private void applyOwner(EndpointDevice device, UUID ownerId) {
        if (ownerId == null) {
            device.setOwner(null);
            device.setOwnerFirstName(null);
            device.setOwnerLastName(null);
            return;
        }
        DeviceOwner owner = deviceOwnerRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("Owner with id " + ownerId + " not found"));
        device.setOwner(owner);
        device.setOwnerFirstName(owner.getFirstName());
        device.setOwnerLastName(owner.getLastName());
    }
}
