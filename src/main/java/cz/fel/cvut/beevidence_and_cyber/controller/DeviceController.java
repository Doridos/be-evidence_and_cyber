package cz.fel.cvut.beevidence_and_cyber.controller;

import cz.fel.cvut.beevidence_and_cyber.dto.AgentDeploymentRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.AIChatRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.DeviceCreateRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.DeviceOwnerCreateRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.DeviceSubnetScanRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.DeviceUpdateRequest;
import cz.fel.cvut.beevidence_and_cyber.service.AiAnalysisService;
import cz.fel.cvut.beevidence_and_cyber.service.CommandService;
import cz.fel.cvut.beevidence_and_cyber.service.CurrentUserService;
import cz.fel.cvut.beevidence_and_cyber.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final CommandService commandService;
    private final AiAnalysisService aiAnalysisService;
    private final CurrentUserService currentUserService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getAllDevices() {
        return deviceService.getAllDevices();
    }

    @GetMapping("/owners")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getKnownOwners() {
        return deviceService.getKnownOwners();
    }

    @PostMapping("/owners")
    @PreAuthorize("hasRole('ADMIN')")
    public Object createOwner(@Valid @RequestBody DeviceOwnerCreateRequest request) {
        return deviceService.createOwner(request, currentUserService.requireCurrentUser());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public Object getDevice(@PathVariable UUID id) {
        return deviceService.getDevice(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Object createDevice(@Valid @RequestBody DeviceCreateRequest request) {
        return deviceService.createDevice(request, currentUserService.requireCurrentUser());
    }

    @PostMapping("/subnet-scan")
    @PreAuthorize("hasRole('ADMIN')")
    public Object scanSubnet(@Valid @RequestBody DeviceSubnetScanRequest request) {
        return deviceService.scanSubnet(request, currentUserService.requireCurrentUser());
    }

    @PostMapping("/{id}/deploy-agent")
    @PreAuthorize("hasRole('ADMIN')")
    public Object deployAgent(@PathVariable UUID id, @Valid @RequestBody AgentDeploymentRequest request) {
        return deviceService.deployAgent(id, request, currentUserService.requireCurrentUser());
    }

    @PostMapping("/{id}/update-agent")
    @PreAuthorize("hasRole('ADMIN')")
    public Object updateAgent(@PathVariable UUID id, @Valid @RequestBody AgentDeploymentRequest request) {
        return deviceService.updateAgent(id, request, currentUserService.requireCurrentUser());
    }

    @PostMapping("/{id}/uninstall-agent")
    @PreAuthorize("hasRole('ADMIN')")
    public Object uninstallAgent(@PathVariable UUID id, @Valid @RequestBody AgentDeploymentRequest request) {
        return deviceService.uninstallAgent(id, request, currentUserService.requireCurrentUser());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Object updateDevice(@PathVariable UUID id, @RequestBody DeviceUpdateRequest request) {
        return deviceService.updateDevice(id, request, currentUserService.requireCurrentUser());
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public Object archiveDevice(@PathVariable UUID id) {
        return deviceService.archiveDevice(id, currentUserService.requireCurrentUser());
    }

    @GetMapping("/{id}/snapshots")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getSnapshots(@PathVariable UUID id) {
        return deviceService.getSnapshots(id);
    }

    @GetMapping("/{id}/heartbeats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getHeartbeats(@PathVariable UUID id) {
        return deviceService.getHeartbeats(id);
    }

    @GetMapping("/{id}/telemetry")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getTelemetry(@PathVariable UUID id) {
        return deviceService.getTelemetry(id);
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getLogs(@PathVariable UUID id) {
        return deviceService.getLogs(id);
    }

    @GetMapping("/{id}/file-system-events")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getFileSystemEvents(@PathVariable UUID id) {
        return deviceService.getFileSystemEvents(id);
    }

    @GetMapping("/{id}/command-requests")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<?> getCommandRequests(@PathVariable UUID id) {
        return commandService.getCommandRequestsForDevice(id);
    }

    @PostMapping("/{id}/ai-chat")
    @PreAuthorize("hasRole('ADMIN')")
    public Object chatWithAi(@PathVariable UUID id, @Valid @RequestBody AIChatRequest request) {
        return aiAnalysisService.chat(id, request, currentUserService.requireCurrentUser());
    }
}
