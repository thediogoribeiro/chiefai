package com.chiefai.personalagent.integration.telegram;

import org.springframework.stereotype.Component;

@Component
public class FakeTelegramConnector implements TelegramConnector {
    @Override
    public void sendText(String destination, String text, String idempotencyKey) {
    }
}
