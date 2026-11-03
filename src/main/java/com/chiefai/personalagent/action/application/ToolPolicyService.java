package com.chiefai.personalagent.action.application;

import com.chiefai.personalagent.action.domain.RiskLevel;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

@Service
public class ToolPolicyService {
    // This boundary is independent of the YAML supplied to the model.
    private static final Map<String, Set<String>> PERMISSIONS = Map.of(
            "manager", Set.of("internal.ask_clarification", "system.get_status", "system.get_version", "system.request_update", "system.get_update_status"),
            "rucodel", Set.of("teams.search_messages", "gmail.create_draft", "calendar.read_events", "trello.create_card"),
            "health", Set.of("oura.read_metrics", "health.save_metrics", "health.generate_report", "health.get_snapshots", "health.get_trends"),
            "personal", Set.of("personal_tasks.create", "personal_tasks.complete", "tasks.list", "calendar.read_events", "gmail.read_labeled_expenses", "expenses.upsert", "expenses.generate_monthly_analysis", "finance.get_dashboard", "finance.search_expenses")
    );
    private final ToolRegistry registry;
    private final Map<String, Set<String>> configuredPermissions;

    public ToolPolicyService(ToolRegistry registry) {
        this.registry = registry;
        Map<String, Set<String>> loaded = new HashMap<>();
        try (var stream = new ClassPathResource("policies/agent-tools.yml").getInputStream()) {
            var root = new YAMLMapper().readTree(stream);
            for (String agent : PERMISSIONS.keySet()) {
                var tools = root.path(agent).path("allowed-tools");
                if (!tools.isArray()) throw new IllegalStateException("Missing agent tool policy");
                Set<String> allowed = new HashSet<>();
                for (var entry : tools) {
                    String tool = entry.asText();
                    if (!PERMISSIONS.get(agent).contains(tool) || !registry.exists(tool))
                        throw new IllegalStateException("Configured tool policy exceeds the Java domain boundary");
                    allowed.add(tool);
                }
                loaded.put(agent, Set.copyOf(allowed));
            }
        } catch (Exception ex) { throw new IllegalStateException("Invalid policies/agent-tools.yml"); }
        this.configuredPermissions = Map.copyOf(loaded);
    }

    public void assertAllowed(String agentId, String toolName, Collection<String> allowlist) {
        if (allowlist == null || !allowlist.contains(toolName) || !PERMISSIONS.getOrDefault(agentId, Set.of()).contains(toolName) || !configuredPermissions.getOrDefault(agentId, Set.of()).contains(toolName)) {
            throw new SecurityException("Agent " + agentId + " is not allowed to use tool " + toolName);
        }
        registry.definition(toolName);
    }

    public void assertAllowed(String agentId, String toolName) {
        assertAllowed(agentId, toolName, configuredPermissions.getOrDefault(agentId, Set.of()));
    }

    public boolean requiresApproval(ToolDefinition definition) {
        return Set.of(RiskLevel.EXTERNAL_WRITE, RiskLevel.DESTRUCTIVE).contains(definition.riskLevel());
    }
}
