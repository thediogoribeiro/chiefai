package com.chiefai.personalagent;

import com.chiefai.personalagent.action.domain.ActionStatus;
import com.chiefai.personalagent.action.infrastructure.ProposedActionJpaRepository;
import com.chiefai.personalagent.ingestion.domain.EventSource;
import com.chiefai.personalagent.ingestion.infrastructure.IncomingEventJpaRepository;
import com.chiefai.personalagent.integration.openai.FakeLanguageModelGateway;
import com.chiefai.personalagent.integration.telegram.TelegramReplyGateway;
import com.chiefai.personalagent.notification.application.NotificationOutboxService;
import com.chiefai.personalagent.notification.domain.NotificationStatus;
import com.chiefai.personalagent.notification.infrastructure.NotificationOutboxJpaRepository;
import com.chiefai.personalagent.orchestration.application.EventProcessingService;
import com.chiefai.personalagent.orchestration.infrastructure.AgentExecutionJpaRepository;
import com.chiefai.personalagent.orchestration.infrastructure.RoutingDecisionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "personal-agent.workers.enabled=false",
        "personal-agent.telegram.enabled=true",
        "personal-agent.telegram.webhook-secret=test-secret",
        "personal-agent.telegram.bot-token=fake-test-token"
})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TelegramAndSecurityIntegrationTest {
    static final String RUN_ID = UUID.randomUUID().toString();
    static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("personal_agent_telegram_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("CHIEFAI_OWNER_USERNAME", () -> "owner");
        registry.add("CHIEFAI_OWNER_PASSWORD_HASH", () -> new BCryptPasswordEncoder().encode("correct-password"));
        registry.add("CHIEFAI_TELEGRAM_OWNER_USER_ID", () -> "123");
        registry.add("CHIEFAI_TELEGRAM_OWNER_CHAT_ID", () -> "999");
    }

    @org.junit.jupiter.api.AfterAll
    static void stopTestDatabase() { if (postgres != null) postgres.stop(); }

    @Autowired MockMvc mvc;
    @Autowired IncomingEventJpaRepository events;
    @Autowired EventProcessingService processing;
    @Autowired NotificationOutboxService outboxService;
    @Autowired NotificationOutboxJpaRepository outbox;
    @Autowired RoutingDecisionJpaRepository routingDecisions;
    @Autowired AgentExecutionJpaRepository executions;
    @Autowired ProposedActionJpaRepository actions;
    @Autowired FakeLanguageModelGateway fakeLlm;
    @Autowired JdbcTemplate jdbc;

    @MockBean TelegramReplyGateway telegramReplies;

    @BeforeEach
    void resetTelegramMock() {
        jdbc.execute("""
                TRUNCATE TABLE
                  tool_execution,
                  proposed_action,
                  notification_outbox,
                  routing_decision,
                  agent_execution,
                  incoming_event,
                  personal_task
                CASCADE
                """);
        reset(telegramReplies);
    }

    @Test
    void authorizedTelegramUpdateCreatesIncomingEventAndDuplicateDoesNotSendTwice() throws Exception {
        String updateId = uniqueUpdate("authorized");
        mvc.perform(post("/api/v1/integrations/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update(updateId, "123", "999", "/personal Quais são as minhas tarefas abertas?")))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/integrations/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update(updateId, "123", "999", "/personal Quais são as minhas tarefas abertas?")))
                .andExpect(status().isAccepted());

        var event = events.findBySourceAndExternalId(EventSource.TELEGRAM, "telegram-" + updateId).orElseThrow();
        assertThat(events.findAll().stream().filter(e -> ("telegram-" + updateId).equals(e.getExternalId())).count()).isEqualTo(1);

        processing.processOnce();
        processing.processOnce();
        outboxService.sendPending();

        assertThat(executions.countByEventId(event.getId())).isEqualTo(1);
        verify(telegramReplies, times(1)).sendText(eq("999"), anyString());
    }

    @Test
    void commandsRouteDeterministicallyAndGenericTextUsesManager() throws Exception {
        int before = fakeLlm.routeCalls();
        String healthUpdate = uniqueUpdate("health");
        String financeUpdate = uniqueUpdate("finance");
        String genericUpdate = uniqueUpdate("generic");

        postTelegram(healthUpdate, "/health Como evoluiu o meu sono nos últimos 14 dias?");
        postTelegram(financeUpdate, "/finance Quanto gastei este mês por categoria?");
        postTelegram(genericUpdate, "Resume isto de forma pessoal.");

        processing.processOnce();

        var health = events.findBySourceAndExternalId(EventSource.TELEGRAM, "telegram-" + healthUpdate).orElseThrow();
        var finance = events.findBySourceAndExternalId(EventSource.TELEGRAM, "telegram-" + financeUpdate).orElseThrow();
        var generic = events.findBySourceAndExternalId(EventSource.TELEGRAM, "telegram-" + genericUpdate).orElseThrow();

        assertThat(routingDecisions.findByEventId(health.getId()).getFirst().getStrategy()).isEqualTo("DETERMINISTIC");
        assertThat(executions.findByEventId(health.getId())).extracting("agentId").containsExactly("health");
        assertThat(executions.findByEventId(finance.getId())).extracting("agentId").containsExactly("personal");
        assertThat(events.findById(finance.getId()).orElseThrow().getContent().path("text").asText()).startsWith("/personal [finance]");
        assertThat(executions.findByEventId(generic.getId())).extracting("agentId").contains("personal");
        assertThat(fakeLlm.routeCalls()).isGreaterThan(before);
    }

    @Test
    void mixedQuestionCanReturnMoreThanOneTelegramSection() throws Exception {
        reset(telegramReplies);
        String updateId = uniqueUpdate("mixed");
        postTelegram(updateId, "Como dormi esta semana e como estão os meus gastos?");

        processing.processOnce();
        outboxService.sendPending();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegramReplies).sendText(eq("999"), text.capture());
        assertThat(text.getValue()).contains("Saúde").contains("Personal");
    }

    @Test
    void unauthorizedTelegramUserDoesNotCreateEventAndInvalidSecretIsRejected() throws Exception {
        String updateId = uniqueUpdate("denied");
        mvc.perform(post("/api/v1/integrations/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update(updateId, "321", "999", "Mostra os meus gastos")))
                .andExpect(status().isAccepted());

        assertThat(events.findBySourceAndExternalId(EventSource.TELEGRAM, "telegram-" + updateId)).isEmpty();
        assertThat(outbox.findByIdempotencyKey("telegram-denied:" + updateId)).isPresent();

        mvc.perform(post("/api/v1/integrations/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update(uniqueUpdate("bad-secret"), "123", "999", "Olá")))
                .andExpect(status().isForbidden());
    }

    @Test
    void temporaryTelegramSendFailureKeepsRetryableOutboxWithoutDuplicate() throws Exception {
        String updateId = uniqueUpdate("retry");
        postTelegram(updateId, "/personal Olá");
        processing.processOnce();

        doThrow(new IllegalStateException("temporary")).when(telegramReplies).sendText(eq("999"), anyString());
        outboxService.sendPending();

        var event = events.findBySourceAndExternalId(EventSource.TELEGRAM, "telegram-" + updateId).orElseThrow();
        var notification = outbox.findByIdempotencyKey("telegram-reply:" + event.getId()).orElseThrow();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(outbox.findAll().stream().filter(row -> row.getIdempotencyKey().equals("telegram-reply:" + event.getId())).count()).isEqualTo(1);
    }

    @Test
    void externalActionRequestedByTelegramStaysPendingApproval() throws Exception {
        String updateId = uniqueUpdate("trello");
        postTelegram(updateId, "/rucodel cria um cartão Trello para rever a produção");

        processing.processOnce();

        var event = events.findBySourceAndExternalId(EventSource.TELEGRAM, "telegram-" + updateId).orElseThrow();
        assertThat(actions.findAll().stream()
                .filter(action -> event.getId().equals(action.getEventId()))
                .filter(action -> action.getToolName().equals("trello.create_card"))
                .findFirst().orElseThrow().getStatus()).isEqualTo(ActionStatus.PENDING_APPROVAL);
    }

    @Test
    void onlyDirectAuthenticatedTelegramUpdateCommandProposesOnce() throws Exception {
        String updateId = uniqueUpdate("system-update");
        postTelegram(updateId, "/system update");
        postTelegram(updateId, "/system update");
        processing.processOnce();
        assertThat(actions.findAll()).hasSize(1);
        assertThat(actions.findAll().getFirst().getAgentId()).isEqualTo("manager");
        assertThat(actions.findAll().getFirst().getStatus()).isEqualTo(ActionStatus.PENDING_APPROVAL);
        String forwarded = update(uniqueUpdate("forwarded"), "123", "999", "/system update").replace("\"text\":", "\"forward_date\": 123, \"text\":");
        mvc.perform(post("/api/v1/integrations/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON).content(forwarded))
                .andExpect(status().isAccepted());
        processing.processOnce();
        assertThat(actions.findAll()).hasSize(1);
    }

    @Test
    void loginCreatesSessionAndInvalidLoginDoesNotAllowProtectedApis() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(request().sessionAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        org.hamcrest.Matchers.notNullValue()));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/finance/dashboard"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/health/trends"))
                .andExpect(status().isUnauthorized());
    }

    private void postTelegram(String updateId, String text) throws Exception {
        mvc.perform(post("/api/v1/integrations/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update(updateId, "123", "999", text)))
                .andExpect(status().isAccepted());
    }

    private String uniqueUpdate(String name) {
        return Long.toString(Integer.toUnsignedLong((RUN_ID + name).hashCode()));
    }

    private String update(String updateId, String fromId, String chatId, String text) {
        return """
                {
                  "update_id": %s,
                  "message": {
                    "message_id": %s,
                    "date": 1780000000,
                    "from": {"id": %s, "is_bot": false, "first_name": "Owner"},
                    "chat": {"id": %s, "type": "private"},
                    "text": %s
                  }
                }
                """.formatted(updateId, updateId, fromId, chatId,
                com.chiefai.personalagent.shared.Jsonb.value(text).toString());
    }
}
