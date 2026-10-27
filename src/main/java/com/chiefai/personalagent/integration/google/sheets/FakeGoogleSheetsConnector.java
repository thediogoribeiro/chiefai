package com.chiefai.personalagent.integration.google.sheets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Primary
@ConditionalOnProperty(prefix = "personal-agent.google", name = "enabled", havingValue = "false", matchIfMissing = true)
public class FakeGoogleSheetsConnector implements GoogleSheetsConnector {
    private final Map<String, Map<UUID, ExpenseSheetRow>> rows = new ConcurrentHashMap<>();
    private volatile boolean failNextUpsert;
    private final Set<String> ensured = ConcurrentHashMap.newKeySet();

    @Override
    public void ensureExpenseWorkbook(String spreadsheetId) {
        ensured.add(spreadsheetId);
        rows.computeIfAbsent(spreadsheetId, ignored -> new ConcurrentHashMap<>());
    }

    @Override
    public int upsertExpense(String spreadsheetId, ExpenseSheetRow row) {
        if (failNextUpsert) {
            failNextUpsert = false;
            throw new IllegalStateException("Transient fake Google Sheets failure");
        }
        ensureExpenseWorkbook(spreadsheetId);
        Map<UUID, ExpenseSheetRow> sheet = rows.get(spreadsheetId);
        sheet.put(row.expenseId(), row);
        return new ArrayList<>(sheet.keySet()).indexOf(row.expenseId()) + 2;
    }

    @Override
    public void upsertReviewRow(String spreadsheetId, ExpenseSheetRow row, List<String> reviewReasons) {
        ensureExpenseWorkbook(spreadsheetId);
    }

    @Override
    public void writeMonthlySummary(String spreadsheetId, List<MonthlyExpenseSummaryRow> rows) {
        ensureExpenseWorkbook(spreadsheetId);
    }

    @Override
    public void updateDashboard(String spreadsheetId, List<MonthlyExpenseSummaryRow> rows) {
        ensureExpenseWorkbook(spreadsheetId);
    }

    @Override
    public int rowCount(String spreadsheetId) {
        return rows.getOrDefault(spreadsheetId, Map.of()).size();
    }

    @Override
    public boolean hasExpense(String spreadsheetId, UUID expenseId) {
        return rows.getOrDefault(spreadsheetId, Map.of()).containsKey(expenseId);
    }

    public void failNextUpsert() {
        this.failNextUpsert = true;
    }
}
