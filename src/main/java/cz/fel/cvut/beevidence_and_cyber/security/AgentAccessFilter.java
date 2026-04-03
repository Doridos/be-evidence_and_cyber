package cz.fel.cvut.beevidence_and_cyber.security;

import cz.fel.cvut.beevidence_and_cyber.config.AgentAccessProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AgentAccessFilter extends OncePerRequestFilter {

    private final AgentAccessProperties agentAccessProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestPath = resolveRequestPath(request);
        return !(requestPath.startsWith("/agents/") || requestPath.startsWith("/api/v1/agents/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String remoteAddress = normalizeIpAddress(request.getRemoteAddr());
        if (!isAllowedRemoteAddress(remoteAddress)) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Agent IP address is not allowed.");
            return;
        }

        String sharedToken = agentAccessProperties.getSharedToken();
        if (sharedToken != null && !sharedToken.isBlank()) {
            String providedToken = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
            if (providedToken == null || !sharedToken.equals(providedToken)) {
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Agent shared token is invalid.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveRequestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (requestUri == null || requestUri.isBlank()) {
            return "";
        }
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private boolean isAllowedRemoteAddress(String remoteAddress) {
        List<String> allowedIpPatterns = sanitizePatterns(agentAccessProperties.getAllowedIpPatterns());
        if (allowedIpPatterns.isEmpty()) {
            return true;
        }

        return allowedIpPatterns.stream()
                .map(this::toRegex)
                .anyMatch(pattern -> pattern.matcher(remoteAddress).matches());
    }

    private List<String> sanitizePatterns(List<String> allowedIpPatterns) {
        if (allowedIpPatterns == null || allowedIpPatterns.isEmpty()) {
            return List.of();
        }

        List<String> sanitizedPatterns = new ArrayList<>();
        for (String pattern : allowedIpPatterns) {
            if (pattern != null && !pattern.isBlank()) {
                sanitizedPatterns.add(pattern.trim());
            }
        }
        return sanitizedPatterns;
    }

    private Pattern toRegex(String wildcardPattern) {
        String regex = wildcardPattern.trim()
                .replace(".", "\\.")
                .replace("*", ".*");
        return Pattern.compile("^" + regex + "$");
    }

    private String normalizeIpAddress(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return "";
        }
        if (remoteAddress.startsWith("::ffff:")) {
            return remoteAddress.substring(7);
        }
        if ("0:0:0:0:0:0:0:1".equals(remoteAddress)) {
            return "::1";
        }
        return remoteAddress;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader.substring(7);
    }
}
