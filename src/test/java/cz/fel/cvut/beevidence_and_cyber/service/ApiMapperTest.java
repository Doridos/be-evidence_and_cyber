package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.dao.CommandRequest;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.CommandRequestDto;
import cz.fel.cvut.beevidence_and_cyber.enumeration.CommandStatusEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.CommandTypeEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiMapperTest {

    private final ApiMapper apiMapper = new ApiMapper();

    @Test
    public void givenCommandPayloadWithSensitiveKeys_whenToDto_thenMaskSensitiveValues() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname("pc-01");
        User requestedByUser = new User();
        requestedByUser.setId(UUID.randomUUID());
        requestedByUser.setAdUsername("alice");

        CommandRequest commandRequest = new CommandRequest();
        commandRequest.setId(UUID.randomUUID());
        commandRequest.setDevice(device);
        commandRequest.setRequestedByUser(requestedByUser);
        commandRequest.setCommandType(CommandTypeEnum.COLLECT);
        commandRequest.setStatus(CommandStatusEnum.PENDING);
        commandRequest.setCreatedAt(LocalDateTime.now());
        commandRequest.setPayloadJson(Map.of(
                "username", "alice",
                "password", "top-secret",
                "nested", Map.of("clientSecret", "abc123")
        ));

        CommandRequestDto result = apiMapper.toDto(commandRequest);

        assertThat(result.payloadJson()).containsEntry("username", "alice");
        assertThat(result.payloadJson()).containsEntry("password", "********");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) result.payloadJson().get("nested");
        assertThat(nested).containsEntry("clientSecret", "********");
    }
}
