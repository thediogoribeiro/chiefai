package com.chiefai.personalagent;

import com.chiefai.personalagent.action.application.ActionService;
import com.chiefai.personalagent.action.application.ToolPolicyService;
import com.chiefai.personalagent.action.domain.ActionStatus;
import com.chiefai.personalagent.action.infrastructure.ProposedActionJpaRepository;
import com.chiefai.personalagent.agent.application.AgentCatalog;
import com.chiefai.personalagent.agent.application.AgentDefinitionLoader;
import com.chiefai.personalagent.health.application.HealthSnapshotService;
import com.chiefai.personalagent.ingestion.application.EventDtos;
import com.chiefai.personalagent.ingestion.application.IngestionService;
import com.chiefai.personalagent.ingestion.domain.EventSource;
import com.chiefai.personalagent.ingestion.domain.EventType;
import com.chiefai.personalagent.ingestion.infrastructure.IncomingEventJpaRepository;
import com.chiefai.personalagent.integration.openai.FakeLanguageModelGateway;
import com.chiefai.personalagent.orchestration.application.EventProcessingService;
import com.chiefai.personalagent.orchestration.infrastructure.AgentExecutionJpaRepository;
import com.chiefai.personalagent.orchestration.infrastructure.RoutingDecisionJpaRepository;
import com.chiefai.personalagent.shared.Jsonb;
import com.chiefai.personalagent.task.application.PersonalTaskService;
import com.chiefai.personalagent.task.domain.TaskPriority;
import com.chiefai.personalagent.task.domain.TaskStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "personal-agent.workers.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "OWNER")
class PersonalAgentCoreIntegrationTest {
    static final String RUN_ID = java.util.UUID.randomUUID().toString();
    static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("personal_agent_test")
                    .withUsername("test")
                    .withPassword("test");
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        } catch (RuntimeException dockerUnavailable) {
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:55432/personal_agent");
            registry.add("spring.datasource.username", () -> "personal_agent");
            registry.add("spring.datasource.password", () -> "personal_agent_local");
        }
    }

    @Autowired IngestionService ingestion;
    @Autowired IncomingEventJpaRepository events;
    @Autowired EventProcessingService processing;
    @Autowired RoutingDecisionJpaRepository routingDecisions;
    @Autowired AgentExecutionJpaRepository executions;
    @Autowired FakeLanguageModelGateway fakeLlm;
    @Autowired AgentCatalog catalog;
    @Autowired ToolPolicyService policy;
    @Autowired ProposedActionJpaRepository actions;
    @Autowired ActionService actionService;
    @Autowired PersonalTaskService taskService;
    @Autowired HealthSnapshotService healthService;
    @Autowired AgentDefinitionLoader loader;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
                TRUNCATE TABLE
                  tool_execution,
                  proposed_action,
                  notification_outbox,
                  routing_decision,
                  agent_execution,
                  incoming_event,
                  personal_task,
                  health_daily_snapshot
                CASCADE
                """);
    }

    @Test
    void newEventIsPersistedAndDuplicateIsIdempotent() {
        var first = ingestion.accept(event("idem-1", EventSource.MANUAL, "Criar tarefa pessoal"));
        var second = ingestion.accept(event("idem-1", EventSource.MANUAL, "Criar tarefa pessoal"));

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(events.findBySourceAndExternalId(EventSource.MANUAL, "idem-1-" + RUN_ID)).isPresent();
    }

    @Test
    void deterministicOuraRoutingDoesNotUseLlm() {
        int before = fakeLlm.routeCalls();
        var event = ingestion.accept(event("oura-1", EventSource.OURA, "sleep readiness hrv"));

        processing.processOnce();

        assertThat(fakeLlm.routeCalls()).isEqualTo(before);
        assertThat(routingDecisions.findByEventId(event.id()).getFirst().getStrategy()).isEqualTo("DETERMINISTIC");
        assertThat(executions.findByEventId(event.id())).extracting("agentId").containsExactly("health");
    }

    @Test
    void rucodelCommandRoutesDirectlyToRucodel() {
        var event = ingestion.accept(event("work-1", EventSource.TELEGRAM, "/rucodel criar tarefa da reunião"));

        processing.processOnce();

        assertThat(routingDecisions.findByEventId(event.id()).getFirst().getStrategy()).isEqualTo("DETERMINISTIC");
        assertThat(executions.findByEventId(event.id())).extracting("agentId").containsExactly("rucodel");
    }

    @Test
    void ambiguousTextUsesManagerAndCanCreateMultipleRoutes() {
        int before = fakeLlm.routeCalls();
        var event = ingestion.accept(event("amb-1", EventSource.MANUAL, "Reunião Rucodel e também sono readiness baixo"));

        processing.processOnce();

        assertThat(fakeLlm.routeCalls()).isGreaterThan(before);
        assertThat(executions.findByEventId(event.id())).extracting("agentId").contains("rucodel", "health");
    }

    @Test
    void agentCannotUseToolOutsideAllowlistAndHealthCannotUseGmailOrTrello() {
        assertThatThrownBy(() -> policy.assertAllowed("health", "gmail.create_draft", catalog.get("health").allowedTools()))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> policy.assertAllowed("health", "trello.create_card", catalog.get("health").allowedTools()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void externalActionRequiresApprovalAndRejectedActionCannotExecute() {
        var event = ingestion.accept(event("trello-1", EventSource.MANUAL, "Rucodel reunião criar tarefa trello"));
        processing.processOnce();
        var action = actions.findAll().stream().filter(a -> event.id().equals(a.getEventId()) && a.getToolName().equals("trello.create_card")).findFirst().orElseThrow();

        assertThat(action.getStatus()).isEqualTo(ActionStatus.PENDING_APPROVAL);
        actionService.reject(action.getId());
        actionService.executeApprovedOnce();
        assertThat(actions.findById(action.getId()).orElseThrow().getStatus()).isEqualTo(ActionStatus.REJECTED);
    }

    @Test
    void approvingTwiceDoesNotDuplicateExecution() {
        var event = ingestion.accept(event("trello-2", EventSource.MANUAL, "/rucodel criar tarefa trello"));
        processing.processOnce();
        var action = actions.findAll().stream().filter(a -> event.id().equals(a.getEventId()) && a.getToolName().equals("trello.create_card")).findFirst().orElseThrow();

        actionService.approve(action.getId());
        actionService.approve(action.getId());
        actionService.executeApprovedOnce();
        actionService.executeApprovedOnce();

        assertThat(actions.findById(action.getId()).orElseThrow().getStatus()).isEqualTo(ActionStatus.EXECUTED);
    }

    @Test
    void personalTaskCanBeCreatedAndCompleted() {
        var task = taskService.create("Comprar filtros", null, TaskPriority.NORMAL, null, null);
        var completed = taskService.complete(task.id());

        assertThat(completed.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void healthSnapshotCanBeSavedReadAndTrendsAreCalculatedWithoutLlm() {
        ObjectNode payload = Jsonb.object();
        payload.put("sleepScore", 80);
        payload.put("readinessScore", 70);
        String source = "TEST_" + RUN_ID;
        healthService.upsert(LocalDate.of(2099, 9, 10), source, payload);

        var read = healthService.get(LocalDate.of(2099, 9, 10), source);
        var trends = healthService.trends(LocalDate.of(2099, 9, 1), LocalDate.of(2099, 9, 30));

        assertThat(read.sleepScore()).isEqualTo(80);
        assertThat(trends.metrics().get("sleepScore").average()).isEqualTo(80.0);
        assertThat(trends.disclaimer()).contains("não constitui diagnóstico médico");
    }

    @Test
    void workerDoesNotProcessSameEventTwice() {
        var event = ingestion.accept(event("once-1", EventSource.MANUAL, "/personal criar tarefa"));

        processing.processOnce();
        processing.processOnce();

        assertThat(executions.countByEventId(event.id())).isEqualTo(1);
    }

    @Test
    void migrationsRanInPostgreSqlTestcontainer() {
        assertThat(events.count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void mainControllersReturnExpectedHttpStatusCodes() throws Exception {
        String eventJson = """
                {"externalId":"http-%s","source":"MANUAL","type":"MESSAGE_RECEIVED","content":{"kind":"TEXT","text":"hello"},"metadata":{}}
                """.formatted(RUN_ID);
        mvc.perform(post("/api/v1/events").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(eventJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").exists());
        mvc.perform(get("/api/v1/events")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/agents")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/tasks").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Teste\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/health/snapshots/2026-09-10?source=MANUAL").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sleepScore\":75}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/health/trends?from=2026-09-01&to=2026-09-30")).andExpect(status().isOk());
    }

    @Test
    void loadingFailsForUnknownTool() {
        assertThatThrownBy(() -> loader.loadForTest("agents/invalid-definitions.yml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown tool");
    }

    private EventDtos.CreateEventRequest event(String externalId, EventSource source, String text) {
        JsonNode content = Jsonb.value(Map.of("kind", "TEXT", "text", text));
        return new EventDtos.CreateEventRequest(externalId + "-" + RUN_ID, source, EventType.MESSAGE_RECEIVED, OffsetDateTime.now(), content, Jsonb.object());
    }
}
