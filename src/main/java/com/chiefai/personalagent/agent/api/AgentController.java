package com.chiefai.personalagent.agent.api;

import com.chiefai.personalagent.agent.application.AgentCatalog;
import com.chiefai.personalagent.agent.domain.AgentDefinition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {
    private final AgentCatalog catalog;

    public AgentController(AgentCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<AgentSummary> list() {
        return catalog.all().stream().map(AgentSummary::from).toList();
    }

    public record AgentSummary(String id, String description, List<String> knowledgeScopes, List<String> allowedTools, int maxToolCalls, String model) {
        static AgentSummary from(AgentDefinition definition) {
            return new AgentSummary(definition.id(), definition.description(), definition.knowledgeScopes(), definition.allowedTools(), definition.maxToolCalls(), definition.model());
        }
    }
}
