package com.chiefai.personalagent.integration.whatsapp;

import org.springframework.stereotype.Component;

@Component
public class FakeWhatsAppConnector implements WhatsAppConnector {
    @Override
    public void sendText(String destination, String text, String idempotencyKey) {
    }
}
