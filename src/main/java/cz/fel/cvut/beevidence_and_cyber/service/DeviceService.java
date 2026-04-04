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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final EndpointDeviceRepository endpointDeviceRepository;
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
        return endpointDeviceRepository.findAll().stream().map(this::toDetailDto).toList();
    }

    public DeviceDetailDto getDevice(UUID id) {
        return toDetailDto(findDevice(id));
    }

    public EndpointDevice findDevice(UUID id) {
        return endpointDeviceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Device with id " + id + " not found"));
    }

    public EndpointDevice findDeviceByHostname(String hostname) {
        return endpointDeviceRepository.findByHostnameIgnoreCase(hostname)
                .orElseThrow(() -> new NotFoundException("Device with hostname " + hostname + " not found"));
    }

    @Transactional
    public DeviceDetailDto createDevice(DeviceCreateRequest request, User actor) {
        EndpointDevice device = new EndpointDevice();
        device.setAssetTag(request.assetTag());
        device.setHostname(request.hostname());
        device.setFqdn(request.fqdn());
        device.setPrimaryIp(request.primaryIp());
        device.setSite(request.site());
        device.setAgentInstalled(request.agentInstalled());
        device.setStatus(DeviceStatusEnum.ACTIVE);
        device.setDiscoveredAt(LocalDateTime.now());
        EndpointDevice saved = endpointDeviceRepository.save(device);

        auditService.log(actor, ActorSourceEnum.WEB, "CREATE_DEVICE", "DEVICE", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("hostname", saved.getHostname()));
        return toDetailDto(saved);
    }

    @Transactional
    public DeviceDetailDto updateDevice(UUID id, DeviceUpdateRequest request, User actor) {
        EndpointDevice device = findDevice(id);
        if (request.assetTag() != null) {
            device.setAssetTag(request.assetTag());
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
        if (request.agentInstalled() != null) {
            device.setAgentInstalled(request.agentInstalled());
        }
        EndpointDevice saved = endpointDeviceRepository.save(device);
        auditService.log(actor, ActorSourceEnum.WEB, "UPDATE_DEVICE", "DEVICE", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("hostname", saved.getHostname()));
        return toDetailDto(saved);
    }

    @Transactional
    public DeviceDetailDto archiveDevice(UUID id, User actor) {
        EndpointDevice device = findDevice(id);
        device.setStatus(DeviceStatusEnum.ARCHIVED);
        device.setArchivedAt(LocalDateTime.now());
        EndpointDevice saved = endpointDeviceRepository.save(device);
        auditService.log(actor, ActorSourceEnum.WEB, "ARCHIVE_DEVICE", "DEVICE", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("hostname", saved.getHostname()));
        return toDetailDto(saved);
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

    public DeviceDetailDto toDetailDto(EndpointDevice device) {
        return apiMapper.toDto(
                device,
                mapSnapshots(device),
                agentHeartbeatRepository.findByDeviceOrderByLastSeenAtDesc(device).stream().map(apiMapper::toDto).toList(),
                telemetrySampleRepository.findByDeviceOrderByCollectedAtDesc(device).stream().map(apiMapper::toDto).toList(),
                deviceLogEntryRepository.findByDeviceOrderByOccurredAtDesc(device).stream().map(apiMapper::toDto).toList(),
                fileSystemEventRepository.findByDeviceOrderByOccurredAtDesc(device).stream().map(apiMapper::toDto).toList()
        );
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
}
