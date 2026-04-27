package cz.fel.cvut.beevidence_and_cyber.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Maps LDAP/AD directory attributes to {@link AdUserPrincipal} during authentication.
 *
 * <p>Called by {@code ActiveDirectoryLdapAuthenticationProvider} with the
 * {@code DirContextOperations} of the authenticated user's LDAP entry.
 * This means all AD attributes are read from the already-open LDAP connection —
 * no additional query is made to the directory.
 *
 * <p>Attributes read:
 * <ul>
 *   <li>{@code displayName} — full name as set in AD</li>
 *   <li>{@code mail}        — primary email address</li>
 *   <li>{@code department}  — department field</li>
 * </ul>
 */
@Component
@Slf4j
public class AdUserDetailsContextMapper implements UserDetailsContextMapper {

    @Override
    public UserDetails mapUserFromContext(DirContextOperations ctx,
                                          String username,
                                          Collection<? extends GrantedAuthority> authorities) {
        String displayName = safeAttribute(ctx, "displayName");
        String email       = safeAttribute(ctx, "mail");
        String department  = safeAttribute(ctx, "department");

        log.debug(
                "Mapped AD user from LDAP context. username={}, displayName={}, email={}, department={}",
                username,
                displayName,
                email != null ? maskEmail(email) : null,
                department
        );

        return new AdUserPrincipal(username, displayName, email, department, authorities);
    }

    @Override
    public void mapUserToContext(UserDetails user, DirContextAdapter ctx) {
        throw new UnsupportedOperationException("Write to LDAP directory is not supported.");
    }

    private String safeAttribute(DirContextOperations ctx, String attributeName) {
        try {
            String value = ctx.getStringAttribute(attributeName);
            return value != null && !value.isBlank() ? value.trim() : null;
        } catch (Exception exception) {
            log.debug("Could not read LDAP attribute '{}': {}", attributeName, exception.getMessage());
            return null;
        }
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
