package com.chiefai.personalagent.integration.openai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record SpecialistResult(String summary, List<String> facts, List<ActionCall> actions, String reply, double confidence, List<String> warnings) {
    public record ActionCall(String toolName, JsonNode arguments, String reason, boolean requiresApproval) {
    }
}
