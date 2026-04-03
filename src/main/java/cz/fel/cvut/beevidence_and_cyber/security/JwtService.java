package cz.fel.cvut.beevidence_and_cyber.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    @Value("${security.jwt.signing-key}")
    private String signingKey;

    @Value("${security.jwt.expiration-hours:10}")
    private long expirationHours;

    public String generateToken(ApplicationUserPrincipal principal) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("displayName", principal.getDisplayName());
        claims.put("roles", principal.getAuthorities().stream().map(Object::toString).toList());

        Instant now = Instant.now();
        return Jwts.builder()
                .claims(claims)
                .subject(principal.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(secretKey())
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, ApplicationUserPrincipal principal) {
        Claims claims = parseClaims(token);
        return principal.getUsername().equals(claims.getSubject()) && claims.getExpiration().after(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey secretKey() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signingKey.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create JWT signing key", exception);
        }
    }
}
