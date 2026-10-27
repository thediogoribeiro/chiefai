package com.chiefai.personalagent.integration.gmail;

import com.chiefai.personalagent.integration.NamedConnector;

import java.util.List;

public interface GmailConnector extends NamedConnector {
    List<GmailMessage> findMessagesByLabel(String label, int limit);
    void addLabel(String messageId, String label);

    @Override
    default String connectorName() {
        return "gmail";
    }
}
