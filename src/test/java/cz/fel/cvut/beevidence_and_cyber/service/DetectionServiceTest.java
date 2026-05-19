package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.DetectionFinding;
import cz.fel.cvut.beevidence_and_cyber.dao.DetectionRule;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionFindingDto;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionFindingStatusRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionRuleDto;
import cz.fel.cvut.beevidence_and_cyber.dto.DetectionRuleRequest;
import cz.fel.cvut.beevidence_and_cyber.enumeration.FindingStatusEnum;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionFindingEventRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionFindingRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DetectionRuleRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceLogEntryRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.DeviceSnapshotRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.FileSystemEventRepository;
import cz.fel.cvut.beevidence_and_cyber.repository.NetworkInterfaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetectionServiceTest {

    @Mock
    private DetectionRuleRepository detectionRuleRepository;
    @Mock
    private DetectionFindingRepository detectionFindingRepository;
    @Mock
    private DetectionFindingEventRepository detectionFindingEventRepository;
    @Mock
    private DeviceLogEntryRepository deviceLogEntryRepository;
    @Mock
    private FileSystemEventRepository fileSystemEventRepository;
    @Mock
    private DeviceSnapshotRepository deviceSnapshotRepository;
    @Mock
    private NetworkInterfaceRepository networkInterfaceRepository;
    @Mock
    private DeviceService deviceService;
    @Mock
    private AuditService auditService;
    @Mock
    private EmailNotificationService emailNotificationService;

    private DetectionService detectionService;

    @BeforeEach
    void setUp() {
        detectionService = new DetectionService(
                detectionRuleRepository,
                detectionFindingRepository,
                detectionFindingEventRepository,
                deviceLogEntryRepository,
                fileSystemEventRepository,
                deviceSnapshotRepository,
                networkInterfaceRepository,
                deviceService,
                new ApiMapper(),
                auditService,
                emailNotificationService
        );
    }

    @Test
    public void givenRuleRequest_whenCreateRule_thenPersistAndReturnRuleDto() {
        DetectionRule savedRule = new DetectionRule();
        savedRule.setId(UUID.randomUUID());
        savedRule.setCode("USB_DEVICE_CONNECTED");
        savedRule.setName("USB");
        savedRule.setSeverity(cz.fel.cvut.beevidence_and_cyber.enumeration.SeverityLevelEnum.HIGH);
        savedRule.setSourceType(cz.fel.cvut.beevidence_and_cyber.enumeration.DetectionSourceTypeEnum.LOG);
        savedRule.setConditionJson(Map.of("kind", "event_code"));
        savedRule.setEnabled(true);
        when(detectionRuleRepository.save(any(DetectionRule.class))).thenReturn(savedRule);

        DetectionRuleDto result = detectionService.createRule(new DetectionRuleRequest(
                "USB_DEVICE_CONNECTED", "USB", "desc", "HIGH", "LOG", Map.of("kind", "event_code"), true
        ), new User());

        assertThat(result.code()).isEqualTo("USB_DEVICE_CONNECTED");
        assertThat(result.severity()).isEqualTo("HIGH");
    }

    @Test
    public void givenExistingFinding_whenUpdateFindingStatus_thenReturnUpdatedFindingDto() {
        cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice device = new cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname("pc-01");
        DetectionFinding finding = new DetectionFinding();
        finding.setId(UUID.randomUUID());
        finding.setDevice(device);
        finding.setStatus(FindingStatusEnum.OPEN);
        finding.setLastSeenAt(LocalDateTime.now().minusDays(1));
        when(detectionFindingRepository.findById(finding.getId())).thenReturn(Optional.of(finding));
        when(detectionFindingRepository.save(any(DetectionFinding.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DetectionFindingDto result = detectionService.updateFindingStatus(
                finding.getId(),
                new DetectionFindingStatusRequest("resolved"),
                new User()
        );

        assertThat(result.status()).isEqualTo("RESOLVED");
        assertThat(finding.getStatus()).isEqualTo(FindingStatusEnum.RESOLVED);
    }
}
