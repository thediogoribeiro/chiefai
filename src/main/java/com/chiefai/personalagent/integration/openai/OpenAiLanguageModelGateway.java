package com.chiefai.personalagent.integration.openai;

import com.chiefai.personalagent.action.application.ToolDefinition;
import com.chiefai.personalagent.agent.domain.AgentDefinition;
import com.chiefai.personalagent.expenses.application.ExpenseExtraction;
import com.chiefai.personalagent.shared.Jsonb;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "personal-agent.openai", name = "enabled", havingValue = "true")
public class OpenAiLanguageModelGateway implements LanguageModelGateway {
    private static final Logger log = LoggerFactory.getLogger(OpenAiLanguageModelGateway.class);
    private final RestClient client;
    private final AgentModels models;
    private final PersonalAgentProperties properties;

    public OpenAiLanguageModelGateway(PersonalAgentProperties properties, AgentModels models, RestClient.Builder builder) {
        if (properties.openai().apiKey() == null || properties.openai().apiKey().isBlank()) {
            throw new IllegalStateException("OPENAI_ENABLED=true requires OPENAI_API_KEY");
        }
        this.properties = properties;
        this.models = models;
        Duration timeout = properties.openai().timeout();
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(5)) > 0)
            throw new IllegalStateException("OpenAI timeout must be positive and at most five minutes");
        models.managerModel(properties.openai());
        for (String agent : List.of("rucodel", "health", "personal")) models.specialistModel(agent, properties.openai());
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.client = builder.requestFactory(factory).baseUrl("https://api.openai.com")
                .defaultHeader("Authorization", "Bearer " + properties.openai().apiKey())
                .build();
    }

    @Override
    public RouteResult route(String eventText, String managerInstructions, String routingSchema, Collection<AgentDefinition> specialists, int maxRoutes) {
        String specialistDescriptions = specialists.stream().map(a -> a.id() + ": " + a.description()).reduce("", (a, b) -> a + "\n" + b);
        JsonNode response = call(Map.of(
                "model", models.managerModel(properties.openai()),
                "max_output_tokens", models.manager().outputTokens(),
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
                "model", models.specialistModel(agent.id(), properties.openai()),
                "max_output_tokens", models.specialists().outputTokens(),
                "store", false,
                "input", List.of(Map.of("role", "system", "content", instructions + "\nSchema:\n" + schema),
                        Map.of("role", "user", "content", "Context:\n" + knowledgeContext + "\n\nAllowed tools:\n" + tools + "\n\nSegment:\n" + safe(segment))),
                "text", Map.of("format", Map.of("type", "json_schema", "name", "specialist_result", "schema", specialistSchema(schema, tools, agent.maxToolCalls()), "strict", true))
        ));
        return Jsonb.MAPPER.convertValue(extractJson(response), SpecialistResult.class);
    }

    private JsonNode specialistSchema(String baseSchema, Collection<ToolDefinition> tools, int maxCalls) {
        ObjectNode schema = (ObjectNode) Jsonb.value(parseSchema(baseSchema));
        ObjectNode actions = (ObjectNode) schema.path("properties").path("actions");
        actions.put("maxItems", maxCalls);
        var variants = Jsonb.MAPPER.createArrayNode();
        for (ToolDefinition tool : tools) {
            ObjectNode action = Jsonb.object();
            action.put("type", "object"); action.put("additionalProperties", false);
            var required = action.putArray("required");
            for (String key : List.of("toolName", "arguments", "reason", "requiresApproval")) required.add(key);
            ObjectNode properties = action.putObject("properties");
            properties.putObject("toolName").put("type", "string").putArray("enum").add(tool.name());
            properties.set("arguments", tool.argumentSchema());
            properties.putObject("reason").put("type", "string");
            properties.putObject("requiresApproval").put("type", "boolean");
            variants.add(action);
        }
        if (variants.isEmpty()) {
            actions.put("maxItems", 0);
            ObjectNode empty = actions.putObject("items");
            empty.put("type", "object"); empty.putObject("properties"); empty.put("additionalProperties", false); empty.putArray("required");
        } else actions.putObject("items").set("anyOf", variants);
        return schema;
    }

    @Override
    public ExpenseExtraction extractExpense(String trustedText) {
        throw new IllegalStateException("Expense structured extraction through OpenAI is not enabled until an expense schema is configured");
    }

    private JsonNode call(Map<String, Object> body) {
        long started = System.nanoTime();
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                JsonNode response = client.post().uri("/v1/responses").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
                JsonNode usage = response == null ? null : response.path("usage");
                log.info("OpenAI model={} durationMs={} inputTokens={} outputTokens={}", body.get("model"),
                        (System.nanoTime() - started) / 1_000_000,
                        usage == null ? 0 : usage.path("input_tokens").asInt(), usage == null ? 0 : usage.path("output_tokens").asInt());
                return response;
            } catch (HttpServerErrorException.ServiceUnavailable | HttpServerErrorException.InternalServerError ex) {
                if (attempt == 3) throw new IllegalStateException("OpenAI service unavailable");
            } catch (RestClientException ex) {
                throw new IllegalStateException("OpenAI request failed; check model, account access and configuration");
            }
        }
        throw new IllegalStateException("OpenAI request failed");
    }

    private JsonNode extractJson(JsonNode response) {
        if (response == null || "incomplete".equals(response.path("status").asText()))
            throw new IllegalStateException("OpenAI returned an incomplete response");
        JsonNode output = response.path("output");
        for (JsonNode item : output) {
            for (JsonNode content : item.path("content")) {
                if (content.has("text")) {
                    try {
                        return Jsonb.MAPPER.readTree(content.get("text").asText());
                    } catch (Exception e) {
                        throw new IllegalStateException("OpenAI response text is not valid JSON");
                    }
                }
            }
        }
        throw new IllegalStateException("OpenAI response did not include structured text output");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSchema(String schema) {
        try {
            Map<String, Object> parsed = Jsonb.MAPPER.readValue(schema, Map.class);
            parsed.remove("$schema");
            return parsed;
        } catch (Exception e) {
            throw new IllegalStateException("Configured response schema is invalid JSON", e);
        }
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
