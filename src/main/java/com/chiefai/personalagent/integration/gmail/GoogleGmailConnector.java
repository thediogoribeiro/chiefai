package com.chiefai.personalagent.integration.gmail;

import com.chiefai.personalagent.shared.PersonalAgentProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
@ConditionalOnProperty(prefix = "personal-agent.google", name = "enabled", havingValue = "true")
public class GoogleGmailConnector implements GmailConnector {
    private final PersonalAgentProperties properties;

    public GoogleGmailConnector(PersonalAgentProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        if (blank(properties.google().clientId()) || blank(properties.google().clientSecret())) {
            throw new IllegalStateException("GOOGLE_ENABLED=true requires GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET");
        }
    }

    @Override
    public List<GmailMessage> findMessagesByLabel(String label, int limit) {
        throw new IllegalStateException("Google Gmail real API adapter is configured but OAuth token exchange is not completed for this local run");
    }

    @Override
    public void addLabel(String messageId, String label) {
        throw new IllegalStateException("Google Gmail real API adapter is configured but OAuth token exchange is not completed for this local run");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
