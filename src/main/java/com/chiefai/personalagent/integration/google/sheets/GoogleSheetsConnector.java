package com.chiefai.personalagent.integration.google.sheets;

import com.chiefai.personalagent.integration.NamedConnector;

import java.util.List;
import java.util.UUID;

public interface GoogleSheetsConnector extends NamedConnector {
    void ensureExpenseWorkbook(String spreadsheetId);
    int upsertExpense(String spreadsheetId, ExpenseSheetRow row);
    void upsertReviewRow(String spreadsheetId, ExpenseSheetRow row, List<String> reviewReasons);
    void writeMonthlySummary(String spreadsheetId, List<MonthlyExpenseSummaryRow> rows);
    void updateDashboard(String spreadsheetId, List<MonthlyExpenseSummaryRow> rows);
    int rowCount(String spreadsheetId);
    boolean hasExpense(String spreadsheetId, UUID expenseId);

    @Override
    default String connectorName() {
        return "google_sheets";
    }
}
