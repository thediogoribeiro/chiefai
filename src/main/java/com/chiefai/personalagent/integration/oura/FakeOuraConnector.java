package com.chiefai.personalagent.integration.oura;

import com.chiefai.personalagent.shared.Jsonb;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class FakeOuraConnector implements OuraConnector {
    @Override
    public JsonNode readMetrics() {
        return Jsonb.object();
    }
}
