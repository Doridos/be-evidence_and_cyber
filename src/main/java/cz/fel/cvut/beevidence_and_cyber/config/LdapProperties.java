package cz.fel.cvut.beevidence_and_cyber.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.ldap")
public class LdapProperties {
    private boolean enabled;
    private String url;
    private String domain;
    private String rootDn;
    private String userSearchBase;
    private String userSearchFilter;
    private String groupSearchBase;
    private String requiredGroup;
}
