package com.chiefai.personalagent.integration.telegram;

import com.chiefai.personalagent.integration.NamedConnector;

public interface TelegramConnector extends NamedConnector {
    void sendText(String destination, String text, String idempotencyKey);

    @Override
    default String connectorName() { return "telegram"; }
}
