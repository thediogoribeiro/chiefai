package com.chiefai.personalagent.integration.trello;

import com.chiefai.personalagent.shared.Jsonb;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FakeTrelloConnector implements TrelloConnector {
    @Override
    public JsonNode createCard(JsonNode arguments, String idempotencyKey) {
        return Jsonb.value(Map.of("fake", true, "cardId", "fake-trello-card-" + Math.abs(idempotencyKey.hashCode())));
    }
}
