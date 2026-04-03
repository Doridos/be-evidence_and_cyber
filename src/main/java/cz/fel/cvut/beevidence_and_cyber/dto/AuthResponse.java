package cz.fel.cvut.beevidence_and_cyber.dto;

public record AuthResponse(
        String token,
        UserDto user
) {
}
