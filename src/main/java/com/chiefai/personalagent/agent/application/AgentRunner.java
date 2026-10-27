package com.chiefai.personalagent.agent.application;

import com.chiefai.personalagent.action.application.ActionService;
import com.chiefai.personalagent.action.application.ToolPolicyService;
import com.chiefai.personalagent.action.application.ToolRegistry;
import com.chiefai.personalagent.agent.domain.AgentDefinition;
import com.chiefai.personalagent.ingestion.infrastructure.IncomingEventEntity;
import com.chiefai.personalagent.integration.openai.LanguageModelGateway;
import com.chiefai.personalagent.integration.openai.RouteResult;
import com.chiefai.personalagent.integration.openai.SpecialistResult;
import com.chiefai.personalagent.notification.application.NotificationOutboxService;
import com.chiefai.personalagent.orchestration.infrastructure.AgentExecutionEntity;
import com.chiefai.personalagent.orchestration.infrastructure.AgentExecutionJpaRepository;
import com.chiefai.personalagent.shared.Jsonb;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class AgentRunner {
    private final AgentCatalog catalog;
    private final KnowledgeRetriever knowledgeRetriever;
    private final LanguageModelGateway gateway;
    private final ToolRegistry toolRegistry;
    private final ToolPolicyService policy;
    private final AgentExecutionJpaRepository executions;
    private final ActionService actions;
    private final NotificationOutboxService outbox;

    public AgentRunner(AgentCatalog catalog, KnowledgeRetriever knowledgeRetriever, LanguageModelGateway gateway, ToolRegistry toolRegistry,
                       ToolPolicyService policy, AgentExecutionJpaRepository executions, ActionService actions, NotificationOutboxService outbox) {
        this.catalog = catalog;
        this.knowledgeRetriever = knowledgeRetriever;
        this.gateway = gateway;
        this.toolRegistry = toolRegistry;
        this.policy = policy;
        this.executions = executions;
        this.actions = actions;
        this.outbox = outbox;
    }

    @Transactional
    public SpecialistResult run(IncomingEventEntity event, RouteResult.Route route) {
        AgentDefinition agent = catalog.get(route.agentId());
        AgentExecutionEntity execution = executions.save(AgentExecutionEntity.running(event.getId(), agent.id(), route.segment(), configuredModel(agent)));
        try {
            SpecialistResult result = gateway.runSpecialist(agent, read(agent.instructions()), route.segment(), knowledgeRetriever.retrieve(agent, route.segment()),
                    toolRegistry.definitionsFor(agent.allowedTools()), read(agent.responseSchema()), "");
            execution.complete(Jsonb.value(result), Jsonb.object());
            int count = 0;
            for (SpecialistResult.ActionCall call : result.actions() == null ? java.util.List.<SpecialistResult.ActionCall>of() : result.actions()) {
                if (count >= agent.maxToolCalls()) {
                    break;
                }
                policy.assertAllowed(agent.id(), call.toolName(), agent.allowedTools());
                actions.proposeOrAutoExecute(event.getId(), execution.getId(), agent.id(), call.toolName(), call.arguments(), call.reason(), call.requiresApproval());
                count++;
            }
            if (result.reply() != null && !result.reply().isBlank()) {
                outbox.enqueue("LOCAL", null, Jsonb.value(Map.of("eventId", event.getId().toString(), "agentId", agent.id(), "reply", result.reply())),
                        "reply:" + event.getId() + ":" + agent.id());
            }
            return result;
        } catch (RuntimeException ex) {
            execution.fail(ex.getMessage());
            throw ex;
        }
    }

    private String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    private String configuredModel(AgentDefinition agent) {
        return agent.model() == null || agent.model().startsWith("${") ? "fake-local" : agent.model();
    }
}
