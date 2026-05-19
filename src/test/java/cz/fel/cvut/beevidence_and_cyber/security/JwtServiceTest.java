package cz.fel.cvut.beevidence_and_cyber.security;

import cz.fel.cvut.beevidence_and_cyber.dao.User;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "signingKey", "test-signing-key");
        ReflectionTestUtils.setField(jwtService, "expirationHours", 10L);
    }

    @Test
    public void givenValidPrincipal_whenGenerateToken_thenReturnTokenWithExpectedUsername() {
        ApplicationUserPrincipal principal = createPrincipal("alice");

        String token = jwtService.generateToken(principal);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtService.isTokenValid(token, principal)).isTrue();
    }

    @Test
    public void givenTokenForDifferentPrincipal_whenValidateToken_thenReturnFalse() {
        ApplicationUserPrincipal tokenOwner = createPrincipal("alice");
        ApplicationUserPrincipal differentPrincipal = createPrincipal("bob");
        String token = jwtService.generateToken(tokenOwner);

        boolean isTokenValid = jwtService.isTokenValid(token, differentPrincipal);

        assertThat(isTokenValid).isFalse();
    }

    @Test
    public void givenExpiredToken_whenValidateToken_thenThrowExpiredJwtException() {
        ReflectionTestUtils.setField(jwtService, "expirationHours", -1L);
        ApplicationUserPrincipal principal = createPrincipal("alice");
        String token = jwtService.generateToken(principal);

        assertThatThrownBy(() -> jwtService.isTokenValid(token, principal))
                .isInstanceOf(ExpiredJwtException.class);
    }

    private ApplicationUserPrincipal createPrincipal(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAdUsername(username);
        user.setDisplayName(username.toUpperCase());
        user.setEnabled(true);
        return new ApplicationUserPrincipal(user, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
