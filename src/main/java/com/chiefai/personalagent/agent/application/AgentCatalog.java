package com.chiefai.personalagent.agent.application;

import com.chiefai.personalagent.agent.domain.AgentDefinition;
import com.chiefai.personalagent.shared.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

@Service
public class AgentCatalog {
    private final Map<String, AgentDefinition> definitions;

    public AgentCatalog(AgentDefinitionLoader loader) {
        this.definitions = loader.load();
    }

    public AgentDefinition get(String id) {
        AgentDefinition definition = definitions.get(id);
        if (definition == null) {
            throw new NotFoundException("Agent not found: " + id);
        }
        return definition;
    }

    public boolean exists(String id) {
        return definitions.containsKey(id);
    }

    public Collection<AgentDefinition> all() {
        return definitions.values();
    }

    public Collection<AgentDefinition> specialists() {
        return definitions.values().stream().filter(agent -> !"manager".equals(agent.id())).toList();
    }
}
