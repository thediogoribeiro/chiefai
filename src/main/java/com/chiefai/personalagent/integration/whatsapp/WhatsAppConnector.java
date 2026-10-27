package com.chiefai.personalagent.integration.whatsapp;

import com.chiefai.personalagent.integration.NamedConnector;

public interface WhatsAppConnector extends NamedConnector {
    void sendText(String destination, String text, String idempotencyKey);

    @Override
    default String connectorName() { return "whatsapp"; }
}
