package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.config.AgentDeploymentProperties;
import cz.fel.cvut.beevidence_and_cyber.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AgentDeploymentPackageService {

    private final AgentDeploymentProperties deploymentProperties;
    private final Map<String, DeploymentPackageEntry> packages = new ConcurrentHashMap<>();

    public String registerPackage(Path archivePath) {
        cleanupExpiredPackages();
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(deploymentProperties.getPackageTokenTtlSeconds());
        packages.put(token, new DeploymentPackageEntry(archivePath, expiresAt));
        return token;
    }

    public Resource resolvePackage(String token) {
        cleanupExpiredPackages();
        DeploymentPackageEntry entry = packages.get(token);
        if (entry == null || entry.isExpired() || !Files.exists(entry.archivePath())) {
            throw new NotFoundException("Deployment package token was not found or has expired.");
        }
        return new FileSystemResource(entry.archivePath());
    }

    public String buildDownloadUrl(String token) {
        String baseUrl = deploymentProperties.getBackendBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.endsWith("/api/v1")) {
            return baseUrl + "/deployment-packages/" + token;
        }
        return baseUrl + "/api/v1/deployment-packages/" + token;
    }

    private void cleanupExpiredPackages() {
        packages.entrySet().removeIf(entry -> {
            DeploymentPackageEntry packageEntry = entry.getValue();
            if (!packageEntry.isExpired()) {
                return false;
            }
            try {
                Files.deleteIfExists(packageEntry.archivePath());
            } catch (Exception ignored) {
            }
            return true;
        });
    }

    private record DeploymentPackageEntry(Path archivePath, LocalDateTime expiresAt) {
        private boolean isExpired() {
            return expiresAt.isBefore(LocalDateTime.now());
        }
    }
}
