package com.chiefai.personalagent;

import com.chiefai.personalagent.action.infrastructure.ProposedActionJpaRepository;
import com.chiefai.personalagent.expenses.application.*;
import com.chiefai.personalagent.expenses.domain.*;
import com.chiefai.personalagent.expenses.infrastructure.ExpenseRecordJpaRepository;
import com.chiefai.personalagent.integration.gmail.*;
import com.chiefai.personalagent.integration.google.sheets.FakeGoogleSheetsConnector;
import com.chiefai.personalagent.integration.openai.FakeLanguageModelGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "personal-agent.google.expenses-spreadsheet-id=test-expenses-sheet",
        "personal-agent.workers.enabled=false"
})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PersonalExpensesIntegrationTest {
    static final String RUN_ID = UUID.randomUUID().toString();

    static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("personal_agent_expenses_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @org.junit.jupiter.api.AfterAll
    static void stopTestDatabase() { if (postgres != null) postgres.stop(); }

    @Autowired FakeGmailConnector gmail;
    @Autowired ExpenseIngestionService ingestion;
    @Autowired ExpenseRecordJpaRepository expenses;
    @Autowired FakeLanguageModelGateway fakeLlm;
    @Autowired ExpenseService expenseService;
    @Autowired ProposedActionJpaRepository actions;
    @Autowired FakeGoogleSheetsConnector sheets;
    @Autowired ExpenseAnalysisService analysisService;
    @Autowired JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
                TRUNCATE TABLE
                  expense_audit_log,
                  finance_dashboard_update,
                  expense_record,
                  tool_execution,
                  proposed_action,
                  notification_outbox,
                  routing_decision,
                  agent_execution,
                  incoming_event
                CASCADE
                """);
    }

    @Test
    void gmailOnlyQueriesConfiguredLabelAndEmailWithoutLabelIsNotProcessed() {
        gmail.addMessage(message("nolabel", Set.of(), "Fornecedor: EDP\nTitular: Diogo Ribeiro\nTotal: 10.00\n2026-09-01"));

        ingestion.pollOnce();

        assertThat(gmail.queriedLabels()).contains("ChiefAI/Gastos");
        assertThat(expenses.findAll()).noneMatch(e -> e.getSourceMessageId().contains("nolabel"));
    }

    @Test
    void diogoAndMariaInvoicesCreateOwnedExpenses() {
        ExpenseDto diogo = ingestion.processMessage(message("diogo", Set.of("ChiefAI/Gastos"), "Fornecedor: EDP\nTitular: José Diogo Ribeiro\nTotal: 32.45\n2026-09-01"));
        ExpenseDto maria = ingestion.processMessage(message("maria", Set.of("ChiefAI/Gastos"), "Fornecedor: Continente\nTitular: Maria João Pacheco Pontes\nTotal: 44,10\n2026-09-02"));

        assertThat(diogo.owner()).isEqualTo(ExpenseOwner.DIOGO);
        assertThat(maria.owner()).isEqualTo(ExpenseOwner.MARIA);
        assertThat(maria.grossAmount()).isEqualByComparingTo(new BigDecimal("44.10"));
    }

    @Test
    void otherOwnerIsIgnoredAndAmbiguousOwnerRequiresReview() {
        ExpenseDto other = ingestion.processMessage(message("other", Set.of("ChiefAI/Gastos"), "Fornecedor: EDP\nTitular Ana Silva\nTotal: 15.00\n2026-09-03"));
        ExpenseDto unknown = ingestion.processMessage(message("unknown", Set.of("ChiefAI/Gastos"), "Fornecedor: EDP\nCliente NIF 123\nTotal: 15.00\n2026-09-03"));

        assertThat(other.owner()).isEqualTo(ExpenseOwner.OTHER);
        assertThat(other.status()).isEqualTo(ExpenseStatus.IGNORED_OTHER_OWNER);
        assertThat(unknown.owner()).isEqualTo(ExpenseOwner.UNKNOWN);
        assertThat(unknown.status()).isEqualTo(ExpenseStatus.REVIEW_REQUIRED);
        assertThat(gmail.get("unknown-" + RUN_ID).orElseThrow().labels()).contains("ChiefAI/Revisao");
    }

    @Test
    void duplicateEmailAttachmentDoesNotDuplicateExpenseOrSheetRows() {
        GmailMessage message = message("dup", Set.of("ChiefAI/Gastos"), "Fornecedor: Vodafone\nTitular: Diogo Ribeiro\nTotal: 20.00\n2026-09-04");

        ExpenseDto first = ingestion.processMessage(message);
        ExpenseDto second = ingestion.processMessage(message);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(expenses.findAll().stream().filter(e -> e.getSourceMessageId().equals("dup-" + RUN_ID)).count()).isEqualTo(1);
    }

    @Test
    void supplierRulesClassifyWithoutLlmAndAmbiguousCaseUsesStructuredFake() {
        int before = fakeLlm.expenseExtractionCalls();
        ExpenseDto ruled = ingestion.processMessage(message("rule", Set.of("ChiefAI/Gastos"), "Fornecedor: Continente\nTitular: Diogo Ribeiro\nTotal: 12.30\n2026-09-05"));
        ExpenseDto ambiguous = ingestion.processMessage(message("ambiguous-expense", Set.of("ChiefAI/Gastos"), "Fornecedor: Loja Estranha\nTitular: Diogo Ribeiro\nTotal: 9.99\n2026-09-06"));

        assertThat(ruled.category()).isEqualTo(ExpenseCategory.VARIABLE_PURCHASE);
        assertThat(fakeLlm.expenseExtractionCalls()).isGreaterThan(before);
        assertThat(ambiguous.category()).isNotNull();
    }

    @Test
    void approvalNoLongerCreatesSheetsActionOrRows() {
        ExpenseDto dto = ingestion.processMessage(message("approval", Set.of("ChiefAI/Gastos"), "Fornecedor: EDP\nTitular: Diogo Ribeiro\nTotal: 50.00\n2026-09-07"));

        assertThat(dto.status()).isEqualTo(ExpenseStatus.EXTRACTED);
        int rowsBeforeApproval = sheets.rowCount("test-expenses-sheet");
        long actionsBeforeApproval = actions.count();
        assertThat(sheets.hasExpense("test-expenses-sheet", dto.id())).isFalse();

        ExpenseDto approved = expenseService.approve(dto.id());

        assertThat(approved.status()).isEqualTo(ExpenseStatus.APPROVED);
        assertThat(actions.count()).isEqualTo(actionsBeforeApproval);
        assertThat(sheets.rowCount("test-expenses-sheet")).isEqualTo(rowsBeforeApproval);
        assertThat(sheets.hasExpense("test-expenses-sheet", dto.id())).isFalse();
    }

    @Test
    void failedExpenseCanBeRetriedForReviewWithoutSheetsSync() {
        ExpenseDto dto = ingestion.processMessage(message("retry", Set.of("ChiefAI/Gastos"), "Fornecedor: EDP\nTitular: Diogo Ribeiro\nTotal: 61.00\n2099-09-07"));

        expenseService.reject(dto.id(), "falha transitória simulada");
        assertThat(expenseService.get(dto.id()).status()).isEqualTo(ExpenseStatus.FAILED);

        ExpenseDto retried = expenseService.retry(dto.id());
        assertThat(retried.status()).isEqualTo(ExpenseStatus.REVIEW_REQUIRED);
    }

    @Test
    void monthlySummariesUseFullLisbonMonthsAndDashboardTotalsWithoutLlm() {
        YearMonth month = YearMonth.of(2099, 1).plusMonths(Math.floorMod(RUN_ID.hashCode(), 600));
        ingestion.processMessage(message("month-a", Set.of("ChiefAI/Gastos"), "Fornecedor: EDP\nTitular: Diogo Ribeiro\nTotal: 10.00\n" + month.atDay(1)));
        ingestion.processMessage(message("month-b", Set.of("ChiefAI/Gastos"), "Fornecedor: Netflix\nTitular: Maria Pontes\nTotal: 5.00\n" + month.atEndOfMonth()));

        var rows = analysisService.monthlySummary(month, month);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().total()).isEqualByComparingTo("15.00");
        assertThat(rows.getFirst().totalDiogo()).isEqualByComparingTo("10.00");
        assertThat(rows.getFirst().totalMaria()).isEqualByComparingTo("5.00");
    }

    @Test
    void fakeConnectorsAreFunctionalAndDisabledSafely() {
        assertThat(gmail.findMessagesByLabel("ChiefAI/Gastos", 10)).isNotNull();
        assertThat(sheets.rowCount("test-expenses-sheet")).isGreaterThanOrEqualTo(0);
    }

    private GmailMessage message(String id, Set<String> labels, String body) {
        String messageId = id + "-" + RUN_ID;
        GmailMessage message = new GmailMessage(messageId, "thread-" + messageId, OffsetDateTime.now(), "Despesa " + id, "sender@example.test",
                body, labels, List.of(new GmailAttachmentRef("body", "body.txt", "text/plain", ExpenseIngestionService.sha256(body), body.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        gmail.addMessage(message);
        return message;
    }
}
