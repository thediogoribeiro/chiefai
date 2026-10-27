package com.chiefai.personalagent.integration.telegram;

public interface TelegramReplyGateway {
    void sendText(String chatId, String text);
}
