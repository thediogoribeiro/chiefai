package com.chiefai.personalagent;

import com.chiefai.personalagent.expenses.application.ExpenseIngestionService;
import com.chiefai.personalagent.expenses.domain.*;
import com.chiefai.personalagent.expenses.infrastructure.ExpenseRecordJpaRepository;
import com.chiefai.personalagent.integration.gmail.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "personal-agent.workers.enabled=false",
        "personal-agent.finance.export.max-rows=2"
})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "OWNER")
class FinanceDashboardIntegrationTest {
    static final String RUN_ID = UUID.randomUUID().toString();
    static final YearMonth MONTH = YearMonth.of(2175, 1).plusMonths(Math.floorMod(RUN_ID.hashCode(), 240));

    static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("personal_agent_finance_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @org.junit.jupiter.api.AfterAll
    static void stopTestDatabase() { if (postgres != null) postgres.stop(); }

    @Autowired MockMvc mvc;
    @Autowired ExpenseIngestionService ingestion;
    @Autowired ExpenseRecordJpaRepository repository;
    @Autowired FakeGmailConnector gmail;
    @Autowired ObjectMapper mapper;
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
    void dashboardWithoutFiltersAndCombinedFiltersReturnBackendTotals() throws Exception {
        String group = "Dashboard " + RUN_ID;
        seed("dash-a", "EDP " + group, "Diogo Ribeiro", "10.25", MONTH.atDay(2));
        seed("dash-b", "Netflix " + group, "Maria Pontes", "5.75", MONTH.atDay(3));

        mvc.perform(get("/api/v1/finance/dashboard")
                        .param("from", MONTH.atDay(1).toString())
                        .param("to", MONTH.atEndOfMonth().toString())
                        .param("search", group))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.timezone").value("Europe/Lisbon"))
                .andExpect(jsonPath("$.totals.total").value(16.00))
                .andExpect(jsonPath("$.totals.diogo").value(10.25))
                .andExpect(jsonPath("$.totals.maria").value(5.75))
                .andExpect(jsonPath("$.byCategory.length()").value(2));

        mvc.perform(get("/api/v1/finance/dashboard")
                        .param("from", MONTH.atDay(1).toString())
                        .param("to", MONTH.atEndOfMonth().toString())
                        .param("search", group)
                        .param("owner", "DIOGO")
                        .param("category", "FIXED_COST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.total").value(10.25))
                .andExpect(jsonPath("$.totals.expenseCount").value(1));
    }

    @Test
    void previousPeriodComparisonUsesEquivalentLisbonDates() throws Exception {
        String supplier = "Comparacao " + RUN_ID;
        seed("prev", supplier, "Diogo Ribeiro", "10.00", MONTH.minusMonths(1).atDay(2));
        seed("current", supplier, "Diogo Ribeiro", "15.00", MONTH.atDay(2));

        mvc.perform(get("/api/v1/finance/dashboard")
                        .param("from", MONTH.atDay(1).toString())
                        .param("to", MONTH.atEndOfMonth().toString())
                        .param("supplier", supplier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousPeriodComparison.previousTotal").value(10.00))
                .andExpect(jsonPath("$.previousPeriodComparison.variation").value(5.00))
                .andExpect(jsonPath("$.previousPeriodComparison.variationPercentage").value(50.00));
    }

    @Test
    void tableSupportsPaginationSortingPatchApproveRejectAndRetry() throws Exception {
        var dto = seed("table", "Tabela " + RUN_ID, "Cliente NIF 123", "7.00", MONTH.atDay(4));

        mvc.perform(get("/api/v1/finance/expenses")
                        .param("from", MONTH.atDay(1).toString())
                        .param("to", MONTH.atEndOfMonth().toString())
                        .param("supplier", "Tabela " + RUN_ID)
                        .param("page", "0").param("size", "1").param("sort", "grossAmount,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(patch("/api/v1/finance/expenses/{id}", dto.id())
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"owner":"DIOGO","category":"VARIABLE_PURCHASE","subcategory":"compras"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("DIOGO"))
                .andExpect(jsonPath("$.status").value("EXTRACTED"));

        mvc.perform(post("/api/v1/finance/expenses/{id}/approve", dto.id()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(post("/api/v1/finance/expenses/{id}/reject", dto.id())
                        .with(csrf())
                        .contentType("application/json").content("{\"reason\":\"duplicado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mvc.perform(post("/api/v1/finance/expenses/{id}/retry", dto.id()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"));
    }

    @Test
    void exportRespectsFiltersCreatesXlsxSheetsAndProtectsFormulaInjection() throws Exception {
        String supplier = "=Fornecedor " + RUN_ID;
        seed("xlsx-a", supplier, "Diogo Ribeiro", "3.40", MONTH.atDay(5));

        byte[] bytes = mvc.perform(get("/api/v1/finance/expenses/export")
                        .param("from", MONTH.atDay(1).toString())
                        .param("to", MONTH.atEndOfMonth().toString())
                        .param("supplier", supplier))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("chiefai-financas-")))
                .andReturn().getResponse().getContentAsByteArray();

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheet("Resumo")).isNotNull();
            assertThat(workbook.getSheet("Despesas")).isNotNull();
            assertThat(workbook.getSheet("Categorias")).isNotNull();
            assertThat(workbook.getSheet("Despesas").getRow(1).getCell(2).getStringCellValue()).startsWith("'=");
            assertThat(workbook.getSheet("Despesas").getRow(1).getCell(8).getNumericCellValue()).isEqualTo(3.40);
        }
    }

    @Test
    void exportLimitIsEnforced() throws Exception {
        String supplier = "Limite " + RUN_ID;
        seed("limit-a", supplier, "Diogo Ribeiro", "1.00", MONTH.atDay(6));
        seed("limit-b", supplier, "Diogo Ribeiro", "2.00", MONTH.atDay(7));
        seed("limit-c", supplier, "Diogo Ribeiro", "3.00", MONTH.atDay(8));

        mvc.perform(get("/api/v1/finance/expenses/export")
                        .param("from", MONTH.atDay(1).toString())
                        .param("to", MONTH.atEndOfMonth().toString())
                        .param("supplier", supplier))
                .andExpect(status().isBadRequest());
    }

    @Test
    void frontendModulesAndRoutesAreAvailableWithEmptyLoadingAndErrorStates() throws Exception {
        mvc.perform(get("/api/v1/dashboard/modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='overview')].enabled").exists())
                .andExpect(jsonPath("$[?(@.id=='finance')].enabled").exists())
                .andExpect(jsonPath("$[?(@.id=='health')].enabled").exists())
                .andExpect(jsonPath("$[?(@.id=='settings')]").doesNotExist());

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/finance"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ChiefAI")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("app.js")));
        mvc.perform(get("/app/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("A carregar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("errorState")));
    }

    @Test
    void dashboardUpdateIsVisibleAfterExpenseCommitAndNothingIsAutomaticallySentToSheets() throws Exception {
        var dto = seed("commit", "Commit " + RUN_ID, "Diogo Ribeiro", "12.00", MONTH.atDay(9));
        var saved = repository.findById(dto.id()).orElseThrow();

        mvc.perform(get("/api/v1/finance/dashboard")
                        .param("from", MONTH.atDay(1).toString())
                        .param("to", MONTH.atEndOfMonth().toString())
                        .param("supplier", "Commit " + RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastUpdatedAt").exists())
                .andExpect(jsonPath("$.totals.total").value(12.00));

        assertThat(saved.getSyncedAt()).isNull();
    }

    private com.chiefai.personalagent.expenses.application.ExpenseDto seed(String id, String supplier, String owner, String amount, LocalDate date) {
        return ingestion.processMessage(message(id, Set.of("ChiefAI/Gastos"), "Fornecedor: " + supplier + "\nTitular: " + owner + "\nTotal: " + amount + "\n" + date));
    }

    private GmailMessage message(String id, Set<String> labels, String body) {
        String messageId = id + "-" + RUN_ID;
        GmailMessage message = new GmailMessage(messageId, "thread-" + messageId, OffsetDateTime.now(), "Despesa " + id, "sender@example.test",
                body, labels, List.of(new GmailAttachmentRef("body", "body.txt", "text/plain", ExpenseIngestionService.sha256(body), body.getBytes(StandardCharsets.UTF_8))));
        gmail.addMessage(message);
        return message;
    }
}
