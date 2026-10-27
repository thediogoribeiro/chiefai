package com.chiefai.personalagent.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

public final class Jsonb {
    public static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private Jsonb() {
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static JsonNode value(Object value) {
        return MAPPER.valueToTree(value);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(JsonNode node) {
        return MAPPER.convertValue(node == null ? object() : node, Map.class);
    }
}
