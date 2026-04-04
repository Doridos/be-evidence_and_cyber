package cz.fel.cvut.beevidence_and_cyber.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.agent.deployment")
public class AgentDeploymentProperties {
    private boolean enabled = true;
    private String pythonExecutable = "python3";
    private String helperScriptPath = "scripts/agent_winrm_deploy.py";
    private String packageScriptsDir = "../agent-evidence_and_cyber/windows";
    private String packageJarDir = "../agent-evidence_and_cyber/target";
    private String packageRuntimeDir = "../agent-evidence_and_cyber/windows-runtime";
    private String remoteStagingDir = "C:\\Windows\\Temp\\EvidenceAndCyberAgentInstall";
    private String backendBaseUrl = "http://192.168.68.106:8080";
    private long packageTokenTtlSeconds = 900;
    private String winrmScheme = "http";
    private int winrmPort = 5985;
    private String winrmTransport = "ntlm";
    private String serverCertValidation = "ignore";
}
