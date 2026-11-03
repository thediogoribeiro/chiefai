package com.chiefai.personalagent.agent.application;

import com.chiefai.personalagent.action.application.ToolRegistry;
import com.chiefai.personalagent.action.application.ToolPolicyService;
import com.chiefai.personalagent.integration.openai.AgentModels;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import com.chiefai.personalagent.agent.domain.AgentDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AgentDefinitionLoader {
    private final ToolRegistry toolRegistry;
    private final ToolPolicyService policy;
    private final AgentModels models;
    private final PersonalAgentProperties properties;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public AgentDefinitionLoader(ToolRegistry toolRegistry, ToolPolicyService policy, AgentModels models, PersonalAgentProperties properties) {
        this.toolRegistry = toolRegistry;
        this.policy = policy;
        this.models = models;
        this.properties = properties;
    }

    public Map<String, AgentDefinition> load() {
        try {
            Definitions definitions = yaml.readValue(new ClassPathResource("agents/definitions.yml").getInputStream(), Definitions.class);
            if (definitions.agents == null || definitions.agents.isEmpty()) {
                throw new IllegalStateException("agents/definitions.yml must contain at least one agent");
            }
            Map<String, AgentDefinition> map = definitions.agents.stream().collect(Collectors.toMap(AgentDefinition::id, Function.identity(), (a, b) -> {
                throw new IllegalStateException("Duplicate agent id: " + a.id());
            }, LinkedHashMap::new));
            for (AgentDefinition agent : map.values()) {
                validate(agent);
            }
            map.replaceAll((id, agent) -> new AgentDefinition(agent.id(), agent.description(), agent.instructions(), agent.knowledgeScopes(),
                    agent.allowedTools(), agent.responseSchema(), "manager".equals(id) ? models.managerModel(properties.openai())
                    : models.specialistModel(id, properties.openai()), agent.maxToolCalls(), agent.approvalRules()));
            return Collections.unmodifiableMap(map);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load agents/definitions.yml: " + e.getMessage(), e);
        }
    }

    public Map<String, AgentDefinition> loadForTest(String path) {
        try {
            Definitions definitions = yaml.readValue(new ClassPathResource(path).getInputStream(), Definitions.class);
            for (AgentDefinition agent : definitions.agents) {
                validate(agent);
            }
            return definitions.agents.stream().collect(Collectors.toMap(AgentDefinition::id, Function.identity()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + path + ": " + e.getMessage(), e);
        }
    }

    private void validate(AgentDefinition agent) {
        require(agent.id(), "agent id");
        require(agent.description(), "description for " + agent.id());
        require(agent.instructions(), "instructions for " + agent.id());
        require(agent.responseSchema(), "response schema for " + agent.id());
        exists(agent.instructions(), "instructions for " + agent.id());
        exists(agent.responseSchema(), "schema for " + agent.id());
        if (agent.knowledgeScopes() != null) {
            agent.knowledgeScopes().forEach(path -> exists(path, "knowledge scope for " + agent.id()));
        }
        if (agent.allowedTools() == null) {
            throw new IllegalStateException("Agent " + agent.id() + " has no allowedTools list");
        }
        for (String tool : agent.allowedTools()) {
            if (!toolRegistry.exists(tool)) {
                throw new IllegalStateException("Agent " + agent.id() + " references unknown tool: " + tool);
            }
            policy.assertAllowed(agent.id(), tool, agent.allowedTools());
        }
        if (agent.maxToolCalls() < 0 || agent.maxToolCalls() > 10) {
            throw new IllegalStateException("Agent " + agent.id() + " maxToolCalls must be between 0 and 10");
        }
    }

    private void exists(String path, String label) {
        if (!new ClassPathResource(path).exists()) {
            throw new IllegalStateException("Missing " + label + ": " + path);
        }
    }

    private void require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + label);
        }
    }

    private static class Definitions {
        public List<AgentDefinition> agents = List.of();
    }
}
