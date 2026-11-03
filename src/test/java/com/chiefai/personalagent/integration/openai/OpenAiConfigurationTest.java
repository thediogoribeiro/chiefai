package com.chiefai.personalagent.integration.openai;

import com.chiefai.personalagent.action.application.ToolDefinition;
import com.chiefai.personalagent.action.domain.RiskLevel;
import com.chiefai.personalagent.agent.domain.AgentDefinition;
import com.chiefai.personalagent.shared.Jsonb;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class OpenAiConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AgentModels.class)
    static class ModelsConfiguration { }
    private ApplicationContextRunner configuration() {
        return new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(ModelsConfiguration.class);
    }
    @Test void importedYamlResolvesPerAgentOverrides() {
        configuration().withPropertyValues("OPENAI_MANAGER_MODEL=manager-choice", "OPENAI_SPECIALIST_MODEL=cheap-choice",
                "OPENAI_HEALTH_MODEL=health-choice", "OPENAI_MANAGER_MAX_OUTPUT_TOKENS=3072")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var models = context.getBean(AgentModels.class);
                    assertThat(models.managerModel(null)).isEqualTo("manager-choice");
                    assertThat(models.manager().outputTokens()).isEqualTo(3072);
                    assertThat(models.specialistModel("health", null)).isEqualTo("health-choice");
                    assertThat(models.specialistModel("rucodel", null)).isEqualTo("cheap-choice");
                });
    }
    @Test void invalidConfigurationDoesNotLogSecretValues(CapturedOutput output) {
        String secret = "sk-test-never-expose";
        configuration().withPropertyValues("OPENAI_MANAGER_MODEL=" + secret).run(c -> assertThat(c).hasFailed());
        configuration().withPropertyValues("OPENAI_MANAGER_MAX_OUTPUT_TOKENS=" + secret).run(c -> assertThat(c).hasFailed());
        assertThat(output.getAll()).doesNotContain(secret);
    }
    @Test void responsesUseConfiguredModelsLimitsAndClosedToolSchemasWithoutNetwork() throws Exception {
        var requests = new ArrayList<JsonNode>();
        var builder = RestClient.builder().requestInterceptor((request, body, execution) -> {
            requests.add(Jsonb.MAPPER.readTree(body));
            JsonNode text = requests.size() == 1
                    ? Jsonb.value(Map.of("routes", List.of(), "clarificationRequired", false))
                    : Jsonb.value(Map.of("summary", "done", "facts", List.of(), "actions", List.of(), "reply", "ok", "confidence", 1, "warnings", List.of()));
            var response = Jsonb.value(Map.of("output", List.of(Map.of("content", List.of(Map.of("text", text.toString())))),
                    "usage", Map.of("input_tokens", 3, "output_tokens", 5)));
            var stub = new MockClientHttpResponse(response.toString().getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
            stub.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return stub;
        });
        var models = new AgentModels(new AgentModels.Manager("manager-choice", 2048), new AgentModels.Specialists("cheap-choice", 1024, "", "health-choice", ""));
        var gateway = new OpenAiLanguageModelGateway(properties(), models, builder);
        String routeSchema = new ClassPathResource("schemas/routing-result.json").getContentAsString(StandardCharsets.UTF_8);
        String resultSchema = new ClassPathResource("schemas/specialist-result.json").getContentAsString(StandardCharsets.UTF_8);
        var agent = new AgentDefinition("health", "health", "", List.of(), List.of("health.get_trends"), "", "", 3, Map.of());
        gateway.route("example", "route", routeSchema, List.of(agent), 3);
        gateway.runSpecialist(agent, "instructions", "example", "", List.of(new ToolDefinition("health.get_trends", "read", Jsonb.value(Map.of(
                "type", "object", "properties", Map.of(), "additionalProperties", false, "required", List.of())), RiskLevel.READ_ONLY, false)), resultSchema, "");
        assertThat(requests).hasSize(2);
        assertThat(requests.getFirst().path("model").asText()).isEqualTo("manager-choice");
        assertThat(requests.get(1).path("model").asText()).isEqualTo("health-choice");
        assertThat(requests.get(1).path("max_output_tokens").asInt()).isEqualTo(1024);
        assertThat(requests.get(1).path("store").asBoolean()).isFalse();
        var action = requests.get(1).path("text").path("format").path("schema").path("properties").path("actions").path("items").path("anyOf").get(0);
        assertThat(action.path("properties").path("toolName").path("enum").get(0).asText()).isEqualTo("health.get_trends");
        assertThat(action.path("properties").path("arguments").path("additionalProperties").asBoolean(true)).isFalse();
    }
    @Test void apiErrorBodyIsNotExposed(CapturedOutput output) {
        var builder = RestClient.builder().requestInterceptor((request, body, execution) -> new MockClientHttpResponse(
                "sk-test-never-expose private response".getBytes(StandardCharsets.UTF_8), HttpStatus.UNAUTHORIZED));
        var gateway = new OpenAiLanguageModelGateway(properties(), new AgentModels(new AgentModels.Manager("manager", 2048),
                new AgentModels.Specialists("specialist", 1024, "", "", "")), builder);
        assertThatThrownBy(() -> gateway.route("private prompt", "instructions", "{}", List.of(), 3))
                .hasMessageNotContaining("sk-test-never-expose").hasMessageNotContaining("private prompt");
        assertThat(output.getAll()).doesNotContain("sk-test-never-expose", "private prompt");
    }
    private PersonalAgentProperties properties() {
        return new PersonalAgentProperties(null, null, null, null, null, null, null, null, null, null, null,
                new PersonalAgentProperties.OpenAi(true, "sk-test-never-expose", "", "", "", Duration.ofSeconds(2)));
    }
}
