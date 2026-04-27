package cz.fel.cvut.beevidence_and_cyber.config;

import cz.fel.cvut.beevidence_and_cyber.security.AdUserDetailsContextMapper;
import cz.fel.cvut.beevidence_and_cyber.security.JwtAuthenticationFilter;
import cz.fel.cvut.beevidence_and_cyber.security.AgentAccessFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({LdapProperties.class, CorsProperties.class, AgentAccessProperties.class, AgentDeploymentProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private final AgentAccessFilter agentAccessFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AdUserDetailsContextMapper adUserDetailsContextMapper;
    private final LdapProperties ldapProperties;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/deployment-packages/**", "/api/v1/deployment-packages/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/agents/**", "/api/v1/agents/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/agents/**", "/api/v1/agents/**").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .addFilterBefore(agentAccessFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        if (!ldapProperties.isEnabled()) {
            return authentication -> {
                throw new BadCredentialsException("LDAP authentication is disabled. Configure app.ldap.* properties.");
            };
        }

        ActiveDirectoryLdapAuthenticationProvider provider = new ActiveDirectoryLdapAuthenticationProvider(
                ldapProperties.getDomain(),
                ldapProperties.getUrl(),
                ldapProperties.getRootDn()
        );
        provider.setConvertSubErrorCodesToExceptions(true);
        provider.setUseAuthenticationRequestCredentials(true);
        provider.setSearchFilter(ldapProperties.getUserSearchFilter());
        // Maps AD attributes (displayName, mail, department) into AdUserPrincipal
        // during authentication so they are available without an extra LDAP query.
        provider.setUserDetailsContextMapper(adUserDetailsContextMapper);
        return new ProviderManager(provider);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
