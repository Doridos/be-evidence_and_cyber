package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.config.LdapProperties;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dto.AuthResponse;
import cz.fel.cvut.beevidence_and_cyber.dto.LoginRequest;
import cz.fel.cvut.beevidence_and_cyber.security.ApplicationUserPrincipal;
import cz.fel.cvut.beevidence_and_cyber.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final DirectoryService directoryService;
    private final JwtService jwtService;
    private final LdapProperties ldapProperties;

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        validateRequiredGroup(authentication);
        String username = authentication.getName();
        User user = directoryService.ensureUserExists(username, username);
        directoryService.updateLastLogin(user);
        ApplicationUserPrincipal principal = directoryService.loadPrincipalByUsername(username);

        return new AuthResponse(
                jwtService.generateToken(principal),
                directoryService.toUserDto(user)
        );
    }

    private void validateRequiredGroup(Authentication authentication) {
        String requiredGroup = normalizeGroupName(ldapProperties.getRequiredGroup());
        if (requiredGroup == null) {
            return;
        }

        boolean memberOfRequiredGroup = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && !authority.isBlank())
                .map(AuthenticationService::normalizeGroupName)
                .anyMatch(requiredGroup::equals);

        if (!memberOfRequiredGroup) {
            throw new AccessDeniedException(
                    "User is not a member of the required Active Directory group: " + ldapProperties.getRequiredGroup()
            );
        }
    }

    private static String normalizeGroupName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmedValue = value.trim();
        if (!trimmedValue.contains("=")) {
            return trimmedValue.toLowerCase(Locale.ROOT);
        }

        try {
            LdapName ldapName = new LdapName(trimmedValue);
            for (Rdn rdn : ldapName.getRdns()) {
                if ("cn".equalsIgnoreCase(rdn.getType())) {
                    Object rdnValue = rdn.getValue();
                    return rdnValue == null ? null : rdnValue.toString().trim().toLowerCase(Locale.ROOT);
                }
            }
        } catch (InvalidNameException ignored) {
            return trimmedValue.toLowerCase(Locale.ROOT);
        }

        return trimmedValue.toLowerCase(Locale.ROOT);
    }
}
