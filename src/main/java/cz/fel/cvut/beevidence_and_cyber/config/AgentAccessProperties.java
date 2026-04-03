package cz.fel.cvut.beevidence_and_cyber.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.agent")
public class AgentAccessProperties {
    private String sharedToken = "";
    private List<String> allowedIpPatterns = new ArrayList<>();
}
