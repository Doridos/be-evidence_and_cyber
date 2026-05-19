package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.config.NotificationProperties;
import cz.fel.cvut.beevidence_and_cyber.dao.DetectionFinding;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.enumeration.SeverityLevelEnum;
import cz.fel.cvut.beevidence_and_cyber.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    private NotificationProperties properties;
    private EmailNotificationService emailNotificationService;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        emailNotificationService = new EmailNotificationService(properties, userRepository);
    }

    @Test
    public void givenNotificationsDisabled_whenNotifyNewHighFinding_thenSkipRecipientLookup() {
        properties.setEnabled(false);
        DetectionFinding finding = new DetectionFinding();
        finding.setSeverity(SeverityLevelEnum.HIGH);

        emailNotificationService.notifyNewHighFinding(finding, new EndpointDevice(), "High finding", "desc");

        verifyNoInteractions(userRepository);
    }

    @Test
    public void givenNonHighSeverityFinding_whenNotifyNewHighFinding_thenSkipRecipientLookup() {
        properties.setEnabled(true);
        DetectionFinding finding = new DetectionFinding();
        finding.setSeverity(SeverityLevelEnum.LOW);

        emailNotificationService.notifyNewHighFinding(finding, new EndpointDevice(), "Low finding", "desc");

        verifyNoInteractions(userRepository);
    }
}
