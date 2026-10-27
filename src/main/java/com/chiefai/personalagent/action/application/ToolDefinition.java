package com.chiefai.personalagent.action.application;

import com.chiefai.personalagent.action.domain.RiskLevel;
import com.fasterxml.jackson.databind.JsonNode;

public record ToolDefinition(
        String name,
        String description,
        JsonNode argumentSchema,
        RiskLevel riskLevel,
        boolean writes
) {
}
