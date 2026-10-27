package com.chiefai.personalagent.integration.google.sheets;

import com.chiefai.personalagent.shared.PersonalAgentProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Primary
@ConditionalOnProperty(prefix = "personal-agent.google", name = "enabled", havingValue = "true")
public class RealGoogleSheetsConnector implements GoogleSheetsConnector {
    private final PersonalAgentProperties properties;

    public RealGoogleSheetsConnector(PersonalAgentProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        if (properties.google().expensesSpreadsheetId() == null || properties.google().expensesSpreadsheetId().isBlank()) {
            throw new IllegalStateException("GOOGLE_ENABLED=true requires GOOGLE_EXPENSES_SPREADSHEET_ID");
        }
    }

    @Override public void ensureExpenseWorkbook(String spreadsheetId) { guard(spreadsheetId); }
    @Override public int upsertExpense(String spreadsheetId, ExpenseSheetRow row) { guard(spreadsheetId); throw disabled(); }
    @Override public void upsertReviewRow(String spreadsheetId, ExpenseSheetRow row, List<String> reviewReasons) { guard(spreadsheetId); throw disabled(); }
    @Override public void writeMonthlySummary(String spreadsheetId, List<MonthlyExpenseSummaryRow> rows) { guard(spreadsheetId); throw disabled(); }
    @Override public void updateDashboard(String spreadsheetId, List<MonthlyExpenseSummaryRow> rows) { guard(spreadsheetId); throw disabled(); }
    @Override public int rowCount(String spreadsheetId) { guard(spreadsheetId); return 0; }
    @Override public boolean hasExpense(String spreadsheetId, UUID expenseId) { guard(spreadsheetId); return false; }

    private void guard(String spreadsheetId) {
        if (!properties.google().expensesSpreadsheetId().equals(spreadsheetId)) {
            throw new SecurityException("Google Sheets tool can only access the configured expenses spreadsheet");
        }
    }

    private IllegalStateException disabled() {
        return new IllegalStateException("Real Google Sheets adapter requires OAuth token setup before use");
    }
}
