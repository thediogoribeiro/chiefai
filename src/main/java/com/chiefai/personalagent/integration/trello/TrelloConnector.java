package com.chiefai.personalagent.integration.trello;

import com.chiefai.personalagent.integration.NamedConnector;
import com.fasterxml.jackson.databind.JsonNode;

public interface TrelloConnector extends NamedConnector {
    JsonNode createCard(JsonNode arguments, String idempotencyKey);

    @Override
    default String connectorName() { return "trello"; }
}
