package com.chiefai.personalagent.action.application;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolExecutionResult(boolean success, JsonNode result, String errorMessage) {
    public static ToolExecutionResult success(JsonNode result) {
        return new ToolExecutionResult(true, result, null);
    }

    public static ToolExecutionResult failure(String message) {
        return new ToolExecutionResult(false, null, message);
    }
}
