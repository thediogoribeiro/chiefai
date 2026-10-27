package com.chiefai.personalagent.integration.openai;

import com.chiefai.personalagent.action.application.ToolDefinition;
import com.chiefai.personalagent.agent.domain.AgentDefinition;
import com.chiefai.personalagent.expenses.application.ExpenseExtraction;
import com.chiefai.personalagent.shared.Jsonb;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "personal-agent.openai", name = "enabled", havingValue = "true")
public class OpenAiLanguageModelGateway implements LanguageModelGateway {
    private final RestClient client;
    private final PersonalAgentProperties properties;

    public OpenAiLanguageModelGateway(PersonalAgentProperties properties, RestClient.Builder builder) {
        if (properties.openai().apiKey() == null || properties.openai().apiKey().isBlank()) {
            throw new IllegalStateException("OPENAI_ENABLED=true requires OPENAI_API_KEY");
        }
        this.properties = properties;
        this.client = builder.baseUrl("https://api.openai.com")
                .defaultHeader("Authorization", "Bearer " + properties.openai().apiKey())
                .build();
    }

    @Override
    public RouteResult route(String eventText, String managerInstructions, String routingSchema, Collection<AgentDefinition> specialists, int maxRoutes) {
        String specialistDescriptions = specialists.stream().map(a -> a.id() + ": " + a.description()).reduce("", (a, b) -> a + "\n" + b);
        JsonNode response = call(Map.of(
                "model", configured(properties.openai().routerModel(), properties.openai().model()),
                "store", false,
                "input", List.of(Map.of("role", "system", "content", managerInstructions + "\nSchema:\n" + routingSchema),
                        Map.of("role", "user", "content", "Specialists:\n" + specialistDescriptions + "\n\nEvent:\n" + safe(eventText))),
                "text", Map.of("format", Map.of("type", "json_schema", "name", "routing_result", "schema", parseSchema(routingSchema), "strict", true))
        ));
        return Jsonb.MAPPER.convertValue(extractJson(response), RouteResult.class);
    }

    @Override
    public SpecialistResult runSpecialist(AgentDefinition agent, String instructions, String segment, String knowledgeContext, Collection<ToolDefinition> tools, String schema, String history) {
        JsonNode response = call(Map.of(
                "model", configured(agent.model(), properties.openai().model()),
                "store", false,
                "input", List.of(Map.of("role", "system", "content", instructions + "\nSchema:\n" + schema),
                        Map.of("role", "user", "content", "Context:\n" + knowledgeContext + "\n\nAllowed tools:\n" + tools + "\n\nSegment:\n" + safe(segment))),
                "text", Map.of("format", Map.of("type", "json_schema", "name", "specialist_result", "schema", parseSchema(schema), "strict", true))
        ));
        return Jsonb.MAPPER.convertValue(extractJson(response), SpecialistResult.class);
    }

    @Override
    public ExpenseExtraction extractExpense(String trustedText) {
        throw new IllegalStateException("Expense structured extraction through OpenAI is not enabled until an expense schema is configured");
    }

    private JsonNode call(Map<String, Object> body) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return client.post().uri("/v1/responses").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            } catch (HttpServerErrorException.ServiceUnavailable | HttpServerErrorException.InternalServerError ex) {
                last = ex;
            }
        }
        throw last == null ? new IllegalStateException("OpenAI request failed") : last;
    }

    private JsonNode extractJson(JsonNode response) {
        JsonNode output = response.path("output");
        for (JsonNode item : output) {
            for (JsonNode content : item.path("content")) {
                if (content.has("text")) {
                    try {
                        return Jsonb.MAPPER.readTree(content.get("text").asText());
                    } catch (Exception e) {
                        throw new IllegalStateException("OpenAI response text is not valid JSON", e);
                    }
                }
            }
        }
        throw new IllegalStateException("OpenAI response did not include structured text output");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSchema(String schema) {
        try {
            return Jsonb.MAPPER.readValue(schema, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Configured response schema is invalid JSON", e);
        }
    }

    private String configured(String preferred, String fallback) {
        String value = preferred == null || preferred.isBlank() || preferred.startsWith("${") ? fallback : preferred;
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("OpenAI model must be configured through environment variables");
        }
        return value;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
