package com.chiefai.personalagent.shared;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class InternalApiKeyInterceptor implements WebMvcConfigurer {
    private final PersonalAgentProperties properties;
    private final Environment environment;

    public InternalApiKeyInterceptor(PersonalAgentProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void validateProductionApiKey() {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("production");
        if (production && (properties.apiKey() == null || !properties.apiKey().enabled()
                || properties.apiKey().value() == null || properties.apiKey().value().isBlank())) {
            throw new IllegalStateException("Production profile requires personal-agent.api-key.value");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // API key authentication is handled by Spring Security so session auth is not blocked.
    }
}
