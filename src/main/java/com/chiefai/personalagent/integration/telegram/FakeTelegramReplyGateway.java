package com.chiefai.personalagent.integration.telegram;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Primary
@ConditionalOnProperty(prefix = "personal-agent.telegram", name = "enabled", havingValue = "false", matchIfMissing = true)
public class FakeTelegramReplyGateway implements TelegramReplyGateway {
    private final List<SentMessage> sentMessages = new ArrayList<>();
    private boolean failNext;

    @Override
    public synchronized void sendText(String chatId, String text) {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("Temporary fake Telegram failure");
        }
        sentMessages.add(new SentMessage(chatId, text));
    }

    public synchronized List<SentMessage> sentMessages() {
        return List.copyOf(sentMessages);
    }

    public synchronized void failNext() {
        failNext = true;
    }

    public record SentMessage(String chatId, String text) {
    }
}
