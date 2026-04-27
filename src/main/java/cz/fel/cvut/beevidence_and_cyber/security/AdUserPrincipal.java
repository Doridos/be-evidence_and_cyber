package cz.fel.cvut.beevidence_and_cyber.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Custom UserDetails that carries AD attributes resolved during LDAP authentication.
 * Populated by {@link AdUserDetailsContextMapper} from the DirContextOperations
 * returned by ActiveDirectoryLdapAuthenticationProvider — no extra LDAP query needed.
 */
public class AdUserPrincipal implements UserDetails {

    private final String username;
    private final String displayName;
    private final String email;
    private final String department;
    private final Collection<? extends GrantedAuthority> authorities;

    public AdUserPrincipal(String username,
                           String displayName,
                           String email,
                           String department,
                           Collection<? extends GrantedAuthority> authorities) {
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.department = department;
        this.authorities = authorities;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getDepartment() {
        return department;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    // ── UserDetails contract — credentials never stored here ──────────────────

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
