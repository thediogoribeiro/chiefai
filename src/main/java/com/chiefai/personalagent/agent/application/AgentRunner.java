package com.chiefai.personalagent.agent.application;

import com.chiefai.personalagent.action.application.ActionService;
import com.chiefai.personalagent.action.application.ToolPolicyService;
import com.chiefai.personalagent.action.application.ToolRegistry;
import com.chiefai.personalagent.agent.domain.AgentDefinition;
import com.chiefai.personalagent.ingestion.infrastructure.IncomingEventEntity;
import com.chiefai.personalagent.integration.openai.LanguageModelGateway;
import com.chiefai.personalagent.integration.openai.AgentModels;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import com.chiefai.personalagent.integration.openai.RouteResult;
import com.chiefai.personalagent.integration.openai.SpecialistResult;
import com.chiefai.personalagent.notification.application.NotificationOutboxService;
import com.chiefai.personalagent.orchestration.infrastructure.AgentExecutionEntity;
import com.chiefai.personalagent.orchestration.infrastructure.AgentExecutionJpaRepository;
import com.chiefai.personalagent.shared.Jsonb;
import com.chiefai.personalagent.system.domain.ManagerSystemCommand;
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
    private final AgentModels models;
    private final PersonalAgentProperties properties;

    public AgentRunner(AgentCatalog catalog, KnowledgeRetriever knowledgeRetriever, LanguageModelGateway gateway, ToolRegistry toolRegistry,
                       ToolPolicyService policy, AgentExecutionJpaRepository executions, ActionService actions, NotificationOutboxService outbox, AgentModels models, PersonalAgentProperties properties) {
        this.catalog = catalog;
        this.knowledgeRetriever = knowledgeRetriever;
        this.gateway = gateway;
        this.toolRegistry = toolRegistry;
        this.policy = policy;
        this.executions = executions;
        this.actions = actions;
        this.outbox = outbox;
        this.models = models;
        this.properties = properties;
    }

    @Transactional
    public SpecialistResult run(IncomingEventEntity event, RouteResult.Route route) {
        if (route == null) throw new SecurityException("A central route is required");
        if ("manager".equals(route.agentId())) return runSystemCommand(event, route);
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

    private SpecialistResult runSystemCommand(IncomingEventEntity event, RouteResult.Route route) {
        ManagerSystemCommand command = ManagerSystemCommand.parse(route.segment())
                .orElseThrow(() -> new SecurityException("Manager self-invocation is not allowed"));
        if (command != ManagerSystemCommand.parse(event.getContent().path("text").asText()).orElse(null))
            throw new SecurityException("System commands must match the original message");
        var manager = catalog.get("manager");
        policy.assertAllowed("manager", command.tool(), manager.allowedTools());
        var execution = executions.save(AgentExecutionEntity.running(event.getId(), "manager", route.segment(), "deterministic-java"));
        String reply;
        if (command == ManagerSystemCommand.UPDATE) {
            if (!event.isAuthenticatedOwnerText()) {
                reply = "Atualização recusada: envia /system update numa mensagem direta autenticada do proprietário ou usa a API com sessão OWNER.";
            } else {
                var action = actions.proposeOrAutoExecute(event.getId(), execution.getId(), "manager", command.tool(), Jsonb.object(),
                        "Pedido explícito de atualização do proprietário", true);
                reply = "Atualização proposta: " + action.id() + ". Aprova-a em POST /api/v1/actions/" + action.id() + "/approve com a sessão OWNER.";
            }
        } else {
            var result = actions.executeReadOnly(event.getId(), execution.getId(), "manager", command.tool());
            reply = result.success() ? String.valueOf(result.result()) : result.errorMessage();
        }
        var result = new SpecialistResult("Operação de sistema", java.util.List.of(), java.util.List.of(), reply, 1, java.util.List.of());
        execution.complete(Jsonb.value(result), Jsonb.object());
        return result;
    }

    private String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    private String configuredModel(AgentDefinition agent) {
        return properties.openai() == null || !properties.openai().enabled() ? "fake-local" : models.specialistModel(agent.id(), properties.openai());
    }
}
