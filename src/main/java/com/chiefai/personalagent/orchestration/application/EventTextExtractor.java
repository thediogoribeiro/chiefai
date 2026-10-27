package com.chiefai.personalagent.orchestration.application;

import com.chiefai.personalagent.ingestion.infrastructure.IncomingEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class EventTextExtractor {
    public String extract(IncomingEventEntity event) {
        JsonNode content = event.getContent();
        if (content == null) {
            return "";
        }
        if (content.hasNonNull("text")) {
            return content.get("text").asText();
        }
        return content.toString();
    }
}
