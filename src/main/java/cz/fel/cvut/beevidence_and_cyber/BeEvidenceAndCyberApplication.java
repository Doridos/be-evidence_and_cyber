package cz.fel.cvut.beevidence_and_cyber;

import cz.fel.cvut.beevidence_and_cyber.config.NotificationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(NotificationProperties.class)
public class BeEvidenceAndCyberApplication {

    public static void main(String[] args) {
        SpringApplication.run(BeEvidenceAndCyberApplication.class, args);
    }
}
