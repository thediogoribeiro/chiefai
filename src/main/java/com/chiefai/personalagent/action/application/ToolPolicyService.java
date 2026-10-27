package com.chiefai.personalagent.action.application;

import com.chiefai.personalagent.action.domain.RiskLevel;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;

@Service
public class ToolPolicyService {
    private final ToolRegistry registry;

    public ToolPolicyService(ToolRegistry registry) {
        this.registry = registry;
    }

    public void assertAllowed(String agentId, String toolName, Collection<String> allowlist) {
        if (allowlist == null || !allowlist.contains(toolName)) {
            throw new SecurityException("Agent " + agentId + " is not allowed to use tool " + toolName);
        }
        registry.definition(toolName);
    }

    public boolean requiresApproval(ToolDefinition definition) {
        return Set.of(RiskLevel.EXTERNAL_WRITE, RiskLevel.DESTRUCTIVE).contains(definition.riskLevel());
    }
}
