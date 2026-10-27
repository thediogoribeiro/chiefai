package com.chiefai.personalagent.integration.telegram;

import com.chiefai.personalagent.shared.PersonalAgentProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@Primary
@ConditionalOnProperty(prefix = "personal-agent.telegram", name = "enabled", havingValue = "true")
public class RealTelegramReplyGateway implements TelegramReplyGateway {
    private final PersonalAgentProperties properties;
    private RestClient client;

    public RealTelegramReplyGateway(PersonalAgentProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        if (properties.telegram().botToken() == null || properties.telegram().botToken().isBlank()) {
            throw new IllegalStateException("TELEGRAM_ENABLED=true requires TELEGRAM_BOT_TOKEN");
        }
        client = RestClient.builder()
                .baseUrl("https://api.telegram.org/bot" + properties.telegram().botToken())
                .build();
    }

    @Override
    public void sendText(String chatId, String text) {
        client.post()
                .uri("/sendMessage")
                .body(Map.of("chat_id", chatId, "text", text))
                .retrieve()
                .toBodilessEntity();
    }
}
