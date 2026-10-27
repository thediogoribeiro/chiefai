package com.chiefai.personalagent.action.application;

import com.fasterxml.jackson.databind.JsonNode;

public interface ToolHandler {
    ToolExecutionResult execute(JsonNode arguments, String idempotencyKey);
}
