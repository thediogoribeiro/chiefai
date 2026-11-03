package com.chiefai.personalagent.orchestration.application;

import com.chiefai.personalagent.agent.application.AgentCatalog;
import com.chiefai.personalagent.agent.domain.AgentDefinition;
import com.chiefai.personalagent.ingestion.domain.EventSource;
import com.chiefai.personalagent.ingestion.infrastructure.IncomingEventEntity;
import com.chiefai.personalagent.integration.openai.LanguageModelGateway;
import com.chiefai.personalagent.integration.openai.RouteResult;
import com.chiefai.personalagent.shared.Jsonb;
import com.chiefai.personalagent.system.domain.ManagerSystemCommand;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class RoutingService {
    private final AgentCatalog agents;
    private final LanguageModelGateway gateway;
    private final EventTextExtractor extractor;
    private final PersonalAgentProperties properties;

    public RoutingService(AgentCatalog agents, LanguageModelGateway gateway, EventTextExtractor extractor, PersonalAgentProperties properties) {
        this.agents = agents;
        this.gateway = gateway;
        this.extractor = extractor;
        this.properties = properties;
    }

    public Decision route(IncomingEventEntity event) {
        String text = extractor.extract(event);
        if (ManagerSystemCommand.parse(text).isPresent()) {
            return new Decision("DETERMINISTIC", single("manager", text, "Structured system command"));
        }
        Optional<RouteResult> deterministic = deterministic(event, text);
        if (deterministic.isPresent()) {
            return new Decision("DETERMINISTIC", sanitize(deterministic.get(), text));
        }
        AgentDefinition manager = agents.get("manager");
        RouteResult llm = gateway.route(text, read(manager.instructions()), read(manager.responseSchema()), agents.specialists(), maxRoutes());
        return new Decision("MANAGER_LLM", sanitize(llm, text));
    }

    private Optional<RouteResult> deterministic(IncomingEventEntity event, String text) {
        String hint = event.getMetadata() != null && event.getMetadata().hasNonNull("domainHint")
                ? event.getMetadata().get("domainHint").asText().toLowerCase(Locale.ROOT) : null;
        if (isValidSpecialist(hint)) {
            return Optional.of(single(hint, text, "Valid domainHint"));
        }
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (event.getSource() == EventSource.OURA) {
            return Optional.of(single("health", text, "OURA events route to health"));
        }
        if (event.getSource() == EventSource.GMAIL && hasExpenseLabel(event)) {
            return Optional.of(single("personal", text, "Gmail expense label routes to personal expenses workflow"));
        }
        if (event.getSource() == EventSource.TEAMS && (lower.contains("rucodel") || "rucodel".equals(hint))) {
            return Optional.of(single("rucodel", text, "Rucodel Teams event"));
        }
        if (lower.startsWith("/health")) {
            return Optional.of(single("health", stripCommand(text), "Explicit /health command"));
        }
        if (lower.startsWith("/work") || lower.startsWith("/rucodel")) {
            return Optional.of(single("rucodel", stripCommand(text), "Explicit work command"));
        }
        if (lower.startsWith("/personal")) {
            return Optional.of(single("personal", stripCommand(text), "Explicit personal command"));
        }
        return Optional.empty();
    }

    private RouteResult sanitize(RouteResult result, String fallbackSegment) {
        List<RouteResult.Route> routes = result.routes() == null ? List.of() : result.routes().stream()
                .filter(route -> isValidSpecialist(route.agentId()))
                .limit(maxRoutes())
                .map(route -> new RouteResult.Route(route.agentId(), route.segment() == null || route.segment().isBlank() ? fallbackSegment : route.segment(),
                        Math.max(0, Math.min(1, route.confidence())), route.reason()))
                .toList();
        return new RouteResult(routes, result.clarificationRequired(), result.clarificationQuestion());
    }

    private boolean isValidSpecialist(String id) {
        return id != null && !"manager".equals(id) && agents.exists(id);
    }

    private RouteResult single(String agentId, String segment, String reason) {
        return new RouteResult(List.of(new RouteResult.Route(agentId, segment, 1.0, reason)), false, null);
    }

    private boolean hasExpenseLabel(IncomingEventEntity event) {
        String inputLabel = properties.expenses() == null || properties.expenses().gmail() == null ? "ChiefAI/Gastos" : properties.expenses().gmail().inputLabel();
        if (event.getMetadata() == null) {
            return false;
        }
        if (event.getMetadata().hasNonNull("label") && inputLabel.equals(event.getMetadata().get("label").asText())) {
            return true;
        }
        if (event.getMetadata().has("labels")) {
            for (com.fasterxml.jackson.databind.JsonNode label : event.getMetadata().get("labels")) {
                if (inputLabel.equals(label.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String stripCommand(String text) {
        return text == null ? "" : text.replaceFirst("^/\\S+\\s*", "");
    }

    private int maxRoutes() {
        return properties.routing() == null ? 3 : properties.routing().maxRoutes();
    }

    private String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    public record Decision(String strategy, RouteResult result) {
        public com.fasterxml.jackson.databind.JsonNode routesJson() {
            return Jsonb.value(result.routes());
        }
    }
}
