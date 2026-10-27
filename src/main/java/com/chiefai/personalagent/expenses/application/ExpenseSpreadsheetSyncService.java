package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.domain.ExpenseStatus;
import com.chiefai.personalagent.expenses.infrastructure.ExpenseRecordEntity;
import com.chiefai.personalagent.expenses.infrastructure.ExpenseRecordJpaRepository;
import com.chiefai.personalagent.integration.google.sheets.ExpenseSheetRow;
import com.chiefai.personalagent.integration.google.sheets.GoogleSheetsConnector;
import com.chiefai.personalagent.shared.NotFoundException;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

public class ExpenseSpreadsheetSyncService {
    private final ExpenseRecordJpaRepository expenses;
    private final GoogleSheetsConnector sheets;
    private final PersonalAgentProperties properties;
    private final ExpenseAnalysisService analysisService;
    private final TransactionTemplate transactionTemplate;

    public ExpenseSpreadsheetSyncService(ExpenseRecordJpaRepository expenses, GoogleSheetsConnector sheets, PersonalAgentProperties properties,
                                         ExpenseAnalysisService analysisService, TransactionTemplate transactionTemplate) {
        this.expenses = expenses;
        this.sheets = sheets;
        this.properties = properties;
        this.analysisService = analysisService;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${personal-agent.workers.expense-sync-delay:5000}")
    public void syncPendingWorker() {
        if (properties.workers() != null && !properties.workers().enabled()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> syncPendingOnce());
    }

    @Transactional
    public int syncPendingOnce() {
        int count = 0;
        for (ExpenseRecordEntity expense : expenses.lockByStatus(ExpenseStatus.SYNC_PENDING, PageRequest.of(0, 10))) {
            syncEntity(expense, configuredSpreadsheetId(), false);
            count++;
        }
        return count;
    }

    @Transactional
    public int syncExpense(UUID expenseId, String spreadsheetId) {
        guardSpreadsheet(spreadsheetId);
        ExpenseRecordEntity expense = expenses.findById(expenseId).orElseThrow(() -> new NotFoundException("Expense not found: " + expenseId));
        syncEntity(expense, spreadsheetId, true);
        return 1;
    }

    private void syncEntity(ExpenseRecordEntity expense, String spreadsheetId, boolean rethrow) {
        guardSpreadsheet(spreadsheetId);
        try {
            sheets.ensureExpenseWorkbook(spreadsheetId);
            int row = sheets.upsertExpense(spreadsheetId, toSheetRow(expense));
            sheets.writeMonthlySummary(spreadsheetId, analysisService.monthlySummaryRows());
            sheets.updateDashboard(spreadsheetId, analysisService.monthlySummaryRows());
            expense.markSynced(row);
        } catch (RuntimeException ex) {
            expense.markFailed(ex.getMessage());
            if (rethrow) {
                throw ex;
            }
        }
    }

    private ExpenseSheetRow toSheetRow(ExpenseRecordEntity e) {
        return new ExpenseSheetRow(e.getId(), e.getSourceMessageId(), e.getSourceAttachmentHash(), e.getReceivedAt(), e.getDocumentDate(), e.getDueDate(),
                e.getOwner().name(), e.getSupplierName(), e.getInvoiceOrReceiptNumber(), e.getDescription(), e.getCategory().name(), e.getSubcategory(),
                e.getRecurring(), e.getCurrency(), e.getGrossAmount(), e.getClassificationConfidence().doubleValue(), e.getStatus().name(), e.getSyncedAt());
    }

    private String configuredSpreadsheetId() {
        return properties.google().expensesSpreadsheetId();
    }

    private void guardSpreadsheet(String spreadsheetId) {
        if (spreadsheetId == null || spreadsheetId.isBlank() || !spreadsheetId.equals(configuredSpreadsheetId())) {
            throw new SecurityException("Expense sync can only use GOOGLE_EXPENSES_SPREADSHEET_ID");
        }
    }
}
