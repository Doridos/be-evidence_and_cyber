package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.config.AgentDeploymentProperties;
import cz.fel.cvut.beevidence_and_cyber.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentDeploymentPackageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    public void givenRegisteredExistingArchive_whenResolvePackage_thenReturnFileResource() throws Exception {
        AgentDeploymentProperties properties = new AgentDeploymentProperties();
        properties.setPackageTokenTtlSeconds(60);
        AgentDeploymentPackageService packageService = new AgentDeploymentPackageService(properties);
        Path archive = tempDir.resolve("agent.zip");
        Files.writeString(archive, "content");
        String token = packageService.registerPackage(archive);

        Resource resource = packageService.resolvePackage(token);

        assertThat(resource.exists()).isTrue();
        assertThat(resource.getFilename()).isEqualTo("agent.zip");
    }

    @Test
    public void givenBackendBaseUrlWithoutApiSuffix_whenBuildDownloadUrl_thenAppendApiPrefix() {
        AgentDeploymentProperties properties = new AgentDeploymentProperties();
        properties.setBackendBaseUrl("https://example.test/backend/");
        AgentDeploymentPackageService packageService = new AgentDeploymentPackageService(properties);

        String result = packageService.buildDownloadUrl("abc123");

        assertThat(result).isEqualTo("https://example.test/backend/api/v1/deployment-packages/abc123");
    }

    @Test
    public void givenRegisteredArchiveThatWasDeleted_whenResolvePackage_thenThrowNotFoundException() throws Exception {
        AgentDeploymentProperties properties = new AgentDeploymentProperties();
        AgentDeploymentPackageService packageService = new AgentDeploymentPackageService(properties);
        Path archive = tempDir.resolve("missing.zip");
        Files.writeString(archive, "content");
        String token = packageService.registerPackage(archive);
        Files.deleteIfExists(archive);

        assertThatThrownBy(() -> packageService.resolvePackage(token))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Deployment package token was not found or has expired.");
    }
}
