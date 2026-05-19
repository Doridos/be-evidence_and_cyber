package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.config.CacheConfig;
import cz.fel.cvut.beevidence_and_cyber.dao.*;
import cz.fel.cvut.beevidence_and_cyber.dto.*;
import cz.fel.cvut.beevidence_and_cyber.enumeration.DeviceStatusEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import cz.fel.cvut.beevidence_and_cyber.exception.BadRequestException;
import cz.fel.cvut.beevidence_and_cyber.exception.NotFoundException;
import cz.fel.cvut.beevidence_and_cyber.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final Duration HEARTBEAT_INACTIVITY_THRESHOLD = Duration.ofMinutes(5);

    private final EndpointDeviceRepository endpointDeviceRepository;
    private final DeviceDepartmentRepository deviceDepartmentRepository;
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

    @Cacheable(CacheConfig.DEVICE_LIST_CACHE)
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
                .map(this::toOwnerOptionDto)
                .sorted(Comparator.comparing(DeviceOwnerOptionDto::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<DeviceDepartmentDto> getKnownDepartments() {
        return deviceDepartmentRepository.findAllByOrderByNameAsc().stream()
                .map(department -> new DeviceDepartmentDto(department.getId(), department.getName()))
                .toList();
    }

    @Transactional
    public DeviceOwnerOptionDto createOwner(DeviceOwnerCreateRequest request, User actor) {
        String firstName = normalizeOwnerPart(request.firstName());
        String lastName = normalizeOwnerPart(request.lastName());
        if (firstName == null || lastName == null) {
            throw new BadRequestException("Vlastník musí obsahovat jméno i příjmení.");
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
        return toOwnerOptionDto(owner);
    }

    @Transactional
    public DeviceDepartmentDto createDepartment(DeviceDepartmentCreateRequest request, User actor) {
        String name = normalizeOwnerPart(request.name());
        if (name == null) {
            throw new BadRequestException("Oddělení musí obsahovat název.");
        }

        DeviceDepartment department = deviceDepartmentRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    DeviceDepartment created = new DeviceDepartment();
                    created.setName(name);
                    return deviceDepartmentRepository.save(created);
                });

        auditService.log(actor, ActorSourceEnum.WEB, "CREATE_DEVICE_DEPARTMENT", "DEVICE_DEPARTMENT", department.getId(),
                AuditResultEnum.SUCCESS, Map.of("name", department.getName()));
        return new DeviceDepartmentDto(department.getId(), department.getName());
    }

    @Transactional
    @CacheEvict(value = CacheConfig.DEVICE_LIST_CACHE, allEntries = true)
    public DeviceDetailDto createDevice(DeviceCreateRequest request, User actor) {
        EndpointDevice device = new EndpointDevice();
        device.setAssetTag(request.assetTag());
        device.setInventoryNumber(request.inventoryNumber());
        device.setHostname(request.hostname());
        device.setFqdn(request.fqdn());
        device.setPrimaryIp(request.primaryIp());
        device.setSite(request.site());
        applyOwner(device, request.ownerId());
        applyDepartment(device, request.departmentId());
        device.setAgentInstalled(request.agentInstalled());
        device.setStatus(DeviceStatusEnum.ACTIVE);
        device.setDiscoveredAt(LocalDateTime.now());
        EndpointDevice saved = endpointDeviceRepository.save(device);

        auditService.log(actor, ActorSourceEnum.WEB, "CREATE_DEVICE", "DEVICE", saved.getId(), AuditResultEnum.SUCCESS,
                Map.of("hostname", saved.getHostname()));
        return toSummaryDto(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.DEVICE_LIST_CACHE, allEntries = true)
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
        if (request.departmentId() != null || device.getDepartment() != null) {
            applyDepartment(device, request.departmentId());
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
    @CacheEvict(value = CacheConfig.DEVICE_LIST_CACHE, allEntries = true)
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

    public Page<DeviceSnapshotDto> getSnapshotsPaged(UUID deviceId, int page, int size) {
        EndpointDevice device = findDevice(deviceId);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "collectedAt"));
        return deviceSnapshotRepository.findByDevice(device, pageRequest)
                .map(snapshot -> apiMapper.toDto(
                        snapshot,
                        networkInterfaceRepository.findBySnapshot(snapshot),
                        loggedInSessionRepository.findBySnapshot(snapshot)
                ));
    }

    public Page<TelemetrySampleDto> getTelemetryPaged(UUID deviceId, int page, int size) {
        EndpointDevice device = findDevice(deviceId);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "collectedAt"));
        return telemetrySampleRepository.findByDevice(device, pageRequest).map(apiMapper::toDto);
    }

    public Page<DeviceLogEntryDto> getLogsPaged(UUID deviceId, int page, int size) {
        EndpointDevice device = findDevice(deviceId);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return deviceLogEntryRepository.findByDevice(device, pageRequest).map(apiMapper::toDto);
    }

    public Page<FileSystemEventDto> getFileSystemEventsPaged(UUID deviceId, int page, int size) {
        EndpointDevice device = findDevice(deviceId);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return fileSystemEventRepository.findByDevice(device, pageRequest).map(apiMapper::toDto);
    }

    public Page<AgentHeartbeatDto> getHeartbeatsPaged(UUID deviceId, int page, int size) {
        EndpointDevice device = findDevice(deviceId);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastSeenAt"));
        return agentHeartbeatRepository.findByDevice(device, pageRequest).map(apiMapper::toDto);
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
                resolveCurrentLoggedUser(device),
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
                resolveCurrentLoggedUser(device),
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

    private DeviceOwnerOptionDto toOwnerOptionDto(DeviceOwner owner) {
        String firstName = normalizeOwnerPart(owner.getFirstName());
        String lastName = normalizeOwnerPart(owner.getLastName());
        return new DeviceOwnerOptionDto(
                owner.getId(),
                firstName,
                lastName,
                java.util.stream.Stream.of(firstName, lastName)
                        .filter(value -> value != null && !value.isBlank())
                        .reduce((left, right) -> left + " " + right)
                        .orElse("")
        );
    }

    private void applyDepartment(EndpointDevice device, UUID departmentId) {
        if (departmentId == null) {
            device.setDepartment(null);
            device.setDepartmentName(null);
            return;
        }

        DeviceDepartment department = deviceDepartmentRepository.findById(departmentId)
                .orElseThrow(() -> new NotFoundException("Department with id " + departmentId + " not found"));
        device.setDepartment(department);
        device.setDepartmentName(department.getName());
    }

    private String resolveCurrentLoggedUser(EndpointDevice device) {
        DeviceSnapshot latestSnapshot = deviceSnapshotRepository.findTopByDeviceOrderByVersionNoDesc(device).orElse(null);
        if (latestSnapshot == null) {
            return null;
        }

        String sessionUser = loggedInSessionRepository.findBySnapshot(latestSnapshot).stream()
                .map(this::formatLoggedInUser)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        if (sessionUser != null) {
            return sessionUser;
        }
        return normalizeOwnerPart(latestSnapshot.getCurrentLoggedUser());
    }

    private String formatLoggedInUser(LoggedInSession session) {
        String username = normalizeOwnerPart(session.getUsername());
        String domain = normalizeOwnerPart(session.getDomain());
        if (username == null) {
            return null;
        }
        return domain == null ? username : domain + "\\" + username;
    }
}
