package com.chiefai.personalagent.agent.domain;

import java.util.List;
import java.util.Map;

public record AgentDefinition(
        String id,
        String description,
        String instructions,
        List<String> knowledgeScopes,
        List<String> allowedTools,
        String responseSchema,
        String model,
        int maxToolCalls,
        Map<String, Object> approvalRules
) {
}
