package cz.fel.cvut.beevidence_and_cyber.security;

import cz.fel.cvut.beevidence_and_cyber.dao.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

@Getter
public class ApplicationUserPrincipal implements UserDetails {

    private final UUID id;
    private final String username;
    private final String displayName;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public ApplicationUserPrincipal(User user, Collection<? extends GrantedAuthority> authorities) {
        this.id = user.getId();
        this.username = user.getAdUsername();
        this.displayName = user.getDisplayName();
        this.enabled = user.isEnabled();
        this.authorities = authorities;
    }

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
}
