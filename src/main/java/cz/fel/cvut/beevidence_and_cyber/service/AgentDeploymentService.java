package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.config.AgentAccessProperties;
import cz.fel.cvut.beevidence_and_cyber.config.AgentDeploymentProperties;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.AgentDeploymentRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.AgentDeploymentResultDto;
import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import cz.fel.cvut.beevidence_and_cyber.exception.BadRequestException;
import cz.fel.cvut.beevidence_and_cyber.repository.EndpointDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentDeploymentService {

    private enum AgentRemoteOperation {
        INSTALL("DEPLOY_AGENT", "Agent byl úspěšně vzdáleně nainstalován.", true, "install"),
        UPDATE("UPDATE_AGENT", "Agent byl úspěšně vzdáleně aktualizován.", true, "install"),
        UNINSTALL("UNINSTALL_AGENT", "Agent byl úspěšně vzdáleně odinstalován.", false, "uninstall");

        private final String auditAction;
        private final String successMessage;
        private final boolean agentInstalled;
        private final String helperOperation;

        AgentRemoteOperation(String auditAction, String successMessage, boolean agentInstalled, String helperOperation) {
            this.auditAction = auditAction;
            this.successMessage = successMessage;
            this.agentInstalled = agentInstalled;
            this.helperOperation = helperOperation;
        }
    }

    private final AgentDeploymentProperties deploymentProperties;
    private final AgentAccessProperties agentAccessProperties;
    private final AgentDeploymentPackageService packageService;
    private final AuditService auditService;
    private final EndpointDeviceRepository endpointDeviceRepository;

    @Transactional
    public AgentDeploymentResultDto deployAgent(EndpointDevice device, AgentDeploymentRequest request, User actor) {
        return manageAgent(device, request, actor, AgentRemoteOperation.INSTALL);
    }

    @Transactional
    public AgentDeploymentResultDto updateAgent(EndpointDevice device, AgentDeploymentRequest request, User actor) {
        return manageAgent(device, request, actor, AgentRemoteOperation.UPDATE);
    }

    @Transactional
    public AgentDeploymentResultDto uninstallAgent(EndpointDevice device, AgentDeploymentRequest request, User actor) {
        return manageAgent(device, request, actor, AgentRemoteOperation.UNINSTALL);
    }

    private AgentDeploymentResultDto manageAgent(EndpointDevice device,
                                                 AgentDeploymentRequest request,
                                                 User actor,
                                                 AgentRemoteOperation operation) {
        if (!deploymentProperties.isEnabled()) {
            throw new BadRequestException("Vzdálená instalace agenta je v konfiguraci backendu vypnutá.");
        }

        String targetHost = resolveTargetHost(device, request.targetHost());
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("Starting remote agent operation. operation={}, deviceId={}, deviceHostname={}, targetHost={}, requestedBy={}",
                operation.name(), device.getId(), device.getHostname(), targetHost, actor.getAdUsername());

        try {
            Path packageArchive = preparePackageArchive();
            String packageToken = packageService.registerPackage(packageArchive);
            String packageUrl = packageService.buildDownloadUrl(packageToken);

            runRemoteOperation(targetHost, request, packageUrl, operation);

            device.setAgentInstalled(operation.agentInstalled);
            endpointDeviceRepository.saveAndFlush(device);
            LocalDateTime finishedAt = LocalDateTime.now();
            auditService.log(actor, ActorSourceEnum.WEB, operation.auditAction, "DEVICE", device.getId(), AuditResultEnum.SUCCESS,
                    Map.of("targetHost", targetHost, "deviceHostname", device.getHostname(), "packageUrl", packageUrl, "operation", operation.name()));

            return new AgentDeploymentResultDto(
                    device.getId(),
                    device.getHostname(),
                    targetHost,
                    true,
                    "SUCCESS",
                    operation.successMessage,
                    Path.of(deploymentProperties.getPackageScriptsDir()).toAbsolutePath().normalize().toString(),
                    startedAt,
                    finishedAt,
                    Duration.between(startedAt, finishedAt).toMillis()
            );
        } catch (Exception exception) {
            LocalDateTime finishedAt = LocalDateTime.now();
            auditService.log(actor, ActorSourceEnum.WEB, operation.auditAction, "DEVICE", device.getId(), AuditResultEnum.FAILED,
                    Map.of("targetHost", targetHost, "error", exception.getMessage(), "operation", operation.name()));
            throw new BadRequestException("Vzdálená správa agenta selhala: " + exception.getMessage());
        }
    }

    private String resolveTargetHost(EndpointDevice device, String requestTargetHost) {
        if (requestTargetHost != null && !requestTargetHost.isBlank()) {
            return requestTargetHost.trim();
        }
        if (device.getPrimaryIp() != null && !device.getPrimaryIp().isBlank()) {
            return device.getPrimaryIp().trim();
        }
        if (device.getHostname() != null && !device.getHostname().isBlank()) {
            return device.getHostname().trim();
        }
        throw new BadRequestException("Pro zařízení chybí IP adresa i hostname, nelze určit cílový host.");
    }

    private Path preparePackageArchive() throws IOException {
        Path scriptsDir = Path.of(deploymentProperties.getPackageScriptsDir()).toAbsolutePath().normalize();
        Path jarDir = Path.of(deploymentProperties.getPackageJarDir()).toAbsolutePath().normalize();
        Path runtimeDir = Path.of(deploymentProperties.getPackageRuntimeDir()).toAbsolutePath().normalize();

        if (!Files.isDirectory(scriptsDir)) {
            throw new BadRequestException("Složka s instalačními skripty agenta neexistuje: " + scriptsDir);
        }
        if (!Files.isDirectory(jarDir)) {
            throw new BadRequestException("Složka s buildnutým agent jar neexistuje: " + jarDir);
        }

        Path latestJar = findLatestAgentJar(jarDir);
        Path tempPackageDir = Files.createTempDirectory("evidence-agent-package-");
        try {
            copyPackageFile(scriptsDir.resolve("install-agent.cmd"), tempPackageDir.resolve("install-agent.cmd"));
            copyPackageFile(scriptsDir.resolve("install-agent.ps1"), tempPackageDir.resolve("install-agent.ps1"));
            copyPackageFile(scriptsDir.resolve("uninstall-agent.cmd"), tempPackageDir.resolve("uninstall-agent.cmd"));
            copyPackageFile(scriptsDir.resolve("uninstall-agent.ps1"), tempPackageDir.resolve("uninstall-agent.ps1"));
            copyOptionalPackageFile(scriptsDir.resolve("WinSW-x64.exe"), tempPackageDir.resolve("WinSW-x64.exe"));
            copyOptionalPackageFile(scriptsDir.resolve("WinSW-arm64.exe"), tempPackageDir.resolve("WinSW-arm64.exe"));
            copyOptionalPackageFile(scriptsDir.resolve("WinSW.exe"), tempPackageDir.resolve("WinSW.exe"));

            Properties properties = loadAndCustomizeProperties(scriptsDir.resolve("agent.properties"));
            Path generatedProperties = tempPackageDir.resolve("agent.properties");
            try (var outputStream = Files.newOutputStream(generatedProperties)) {
                properties.store(outputStream, "Generated by backend agent deployment");
            }

            copyPackageFile(latestJar, tempPackageDir.resolve(latestJar.getFileName()));
            copyOptionalRuntime(runtimeDir, tempPackageDir.resolve("runtime"));

            Path archivePath = Files.createTempFile("evidence-agent-package-", ".zip");
            zipDirectory(tempPackageDir, archivePath);
            return archivePath;
        } finally {
            deleteDirectoryRecursively(tempPackageDir);
        }
    }

    private Properties loadAndCustomizeProperties(Path runtimeTemplate) throws IOException {
        if (!Files.exists(runtimeTemplate)) {
            throw new BadRequestException("V deployment balíčku chybí agent.properties: " + runtimeTemplate);
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(runtimeTemplate)) {
            properties.load(inputStream);
        }
        properties.setProperty("agent.backend.base-url", deploymentProperties.getBackendBaseUrl());
        properties.setProperty("agent.backend.token", agentAccessProperties.getSharedToken() == null ? "" : agentAccessProperties.getSharedToken());
        properties.setProperty("agent.heartbeat.interval-seconds", "60");
        properties.setProperty("agent.ui.enabled", "true");
        properties.setProperty("agent.ui.start-minimized", "true");
        properties.setProperty("agent.windows.auto-start-enabled", "false");
        return properties;
    }

    private void copyPackageFile(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            throw new BadRequestException("V deployment balíčku chybí požadovaný soubor: " + source);
        }
        Files.copy(source, target);
    }

    private void copyOptionalPackageFile(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            log.info("Optional deployment file not found, skipping. path={}", source);
            return;
        }
        Files.copy(source, target);
    }

    private void copyOptionalRuntime(Path sourceRuntimeDir, Path targetRuntimeDir) throws IOException {
        Path effectiveRuntimeDir = sourceRuntimeDir;
        if (Files.isDirectory(sourceRuntimeDir.resolve("runtime"))) {
            effectiveRuntimeDir = sourceRuntimeDir.resolve("runtime");
        }

        if (!Files.isDirectory(effectiveRuntimeDir)) {
            log.info("Bundled Windows runtime directory not found, deployment package will rely on system Java. path={}",
                    sourceRuntimeDir);
            return;
        }

        final Path runtimeRoot = effectiveRuntimeDir;
        try (Stream<Path> walk = Files.walk(runtimeRoot)) {
            walk.forEach(sourcePath -> {
                try {
                    Path relativePath = runtimeRoot.relativize(sourcePath);
                    Path targetPath = targetRuntimeDir.resolve(relativePath.toString());
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Path parent = targetPath.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        Files.copy(sourcePath, targetPath);
                    }
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private Path findLatestAgentJar(Path jarDir) throws IOException {
        try (Stream<Path> files = Files.list(jarDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("agent-evidence-and-cyber-"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return !fileName.contains("sources") && !fileName.contains("javadoc") && !fileName.contains("original");
                    })
                    .max(Comparator.comparing(path -> path.toFile().lastModified()))
                    .orElseThrow(() -> new BadRequestException("V target složce nebyl nalezen buildnutý agent jar."));
        }
    }

    private void zipDirectory(Path sourceDirectory, Path archivePath) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(archivePath))) {
            Files.walk(sourceDirectory)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            String entryName = sourceDirectory.relativize(path).toString().replace('\\', '/');
                            zipOutputStream.putNextEntry(new ZipEntry(entryName));
                            Files.copy(path, zipOutputStream);
                            zipOutputStream.closeEntry();
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private void deleteDirectoryRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private void runRemoteOperation(String targetHost,
                                    AgentDeploymentRequest request,
                                    String packageUrl,
                                    AgentRemoteOperation operation) throws IOException, InterruptedException {
        Path helperScriptPath = Path.of(deploymentProperties.getHelperScriptPath()).toAbsolutePath().normalize();
        if (!Files.exists(helperScriptPath)) {
            throw new BadRequestException("Python helper script pro WinRM deployment nebyl nalezen: " + helperScriptPath);
        }

        String pythonExecutable = resolvePythonExecutable();
        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add(helperScriptPath.toString());
        command.add("--target-host");
        command.add(targetHost);
        command.add("--username");
        command.add(request.username());
        command.add("--package-url");
        command.add(packageUrl);
        command.add("--staging-dir");
        command.add(deploymentProperties.getRemoteStagingDir());
        command.add("--operation");
        command.add(operation.helperOperation);
        command.add("--scheme");
        command.add(deploymentProperties.getWinrmScheme());
        command.add("--port");
        command.add(String.valueOf(deploymentProperties.getWinrmPort()));
        command.add("--transport");
        command.add(deploymentProperties.getWinrmTransport());
        command.add("--server-cert-validation");
        command.add(deploymentProperties.getServerCertValidation());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("DEPLOY_AGENT_PASSWORD", request.password());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();
        log.info("Remote agent operation output for {} (operation={}): {}", targetHost, operation.name(), output);

        if (exitCode != 0) {
            throw new BadRequestException(output.isBlank()
                    ? "Python WinRM deployment skončil s chybovým kódem " + exitCode + "."
                    : output);
        }
    }

    private String resolvePythonExecutable() {
        String configuredExecutable = deploymentProperties.getPythonExecutable();
        if (configuredExecutable == null || configuredExecutable.isBlank()) {
            return "python3";
        }
        return configuredExecutable.trim();
    }
}
