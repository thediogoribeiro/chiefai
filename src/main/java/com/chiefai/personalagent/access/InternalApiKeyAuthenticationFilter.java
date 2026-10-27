package com.chiefai.personalagent.access;

import com.chiefai.personalagent.shared.PersonalAgentProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class InternalApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final PersonalAgentProperties properties;

    public InternalApiKeyAuthenticationFilter(PersonalAgentProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (properties.apiKey() != null && properties.apiKey().enabled()) {
            String configured = properties.apiKey().value();
            String provided = request.getHeader("X-Internal-Api-Key");
            if (configured != null && !configured.isBlank() && configured.equals(provided)) {
                var auth = new UsernamePasswordAuthenticationToken("internal-api-key", null,
                        List.of(new SimpleGrantedAuthority("ROLE_OWNER"), new SimpleGrantedAuthority("ROLE_INTERNAL")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
