package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.domain.ExpenseCategory;
import com.chiefai.personalagent.expenses.domain.ExpenseOwner;
import com.chiefai.personalagent.expenses.domain.ExpenseStatus;
import com.chiefai.personalagent.expenses.infrastructure.*;
import com.chiefai.personalagent.shared.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExpenseService {
    private final ExpenseRecordJpaRepository expenses;
    private final ExpenseAuditLogJpaRepository auditLog;
    private final FinanceDashboardUpdateJpaRepository dashboardUpdates;

    public ExpenseService(ExpenseRecordJpaRepository expenses, ExpenseAuditLogJpaRepository auditLog,
                          FinanceDashboardUpdateJpaRepository dashboardUpdates) {
        this.expenses = expenses;
        this.auditLog = auditLog;
        this.dashboardUpdates = dashboardUpdates;
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto> list(ExpenseStatus status) {
        return (status == null ? expenses.findAllByOrderByReceivedAtDesc(PageRequest.of(0, 100)) : expenses.findByStatus(status))
                .stream().map(ExpenseMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ExpenseDto get(UUID id) {
        return expenses.findById(id).map(ExpenseMapper::toDto).orElseThrow(() -> new NotFoundException("Expense not found: " + id));
    }

    @Transactional
    public ExpenseDto approve(UUID id) {
        ExpenseRecordEntity expense = expenses.findById(id).orElseThrow(() -> new NotFoundException("Expense not found: " + id));
        expense.approveForDashboard();
        log(id, "APPROVED", Map.of("target", "chiefai-dashboard"));
        return ExpenseMapper.toDto(expense);
    }

    @Transactional
    public ExpenseDto reject(UUID id, String reason) {
        ExpenseRecordEntity expense = expenses.findById(id).orElseThrow(() -> new NotFoundException("Expense not found: " + id));
        expense.reject(reason);
        log(id, "REJECTED", Map.of("reason", reason == null ? "Rejected" : reason));
        return ExpenseMapper.toDto(expense);
    }

    @Transactional
    public ExpenseDto retrySync(UUID id) {
        return retry(id);
    }

    @Transactional
    public ExpenseDto retry(UUID id) {
        ExpenseRecordEntity expense = expenses.findById(id).orElseThrow(() -> new NotFoundException("Expense not found: " + id));
        expense.retryProcessing();
        log(id, "RETRY", Map.of("target", "expense-review"));
        return ExpenseMapper.toDto(expense);
    }

    @Transactional
    public ExpenseDto correct(UUID id, ExpenseOwner owner, ExpenseCategory category, String subcategory, String description) {
        ExpenseRecordEntity expense = expenses.findById(id).orElseThrow(() -> new NotFoundException("Expense not found: " + id));
        expense.correct(owner, category, subcategory, description);
        log(id, "CORRECTED", Map.of(
                "owner", owner == null ? "" : owner.name(),
                "category", category == null ? "" : category.name(),
                "subcategory", subcategory == null ? "" : subcategory));
        return ExpenseMapper.toDto(expense);
    }

    @Transactional(readOnly = true)
    public List<ExpenseAuditDto> history(UUID id) {
        return auditLog.findByExpenseIdOrderByCreatedAtDesc(id).stream()
                .map(entry -> new ExpenseAuditDto(entry.getId(), entry.getExpenseId(), entry.getAction(), entry.getDetails(), entry.getCreatedAt()))
                .toList();
    }

    private void log(UUID expenseId, String action, Map<String, ?> details) {
        auditLog.save(new ExpenseAuditLogEntity(expenseId, action, details));
        dashboardUpdates.save(new FinanceDashboardUpdateEntity(action, expenseId));
    }

    @Transactional
    public void logExport(long rowCount, Map<String, ?> details) {
        java.util.LinkedHashMap<String, Object> exportDetails = new java.util.LinkedHashMap<>();
        exportDetails.put("rowCount", rowCount);
        exportDetails.putAll(details);
        auditLog.save(new ExpenseAuditLogEntity(null, "EXPORTED", exportDetails));
    }

    public record ExpenseAuditDto(UUID id, UUID expenseId, String action, com.fasterxml.jackson.databind.JsonNode details,
                                  java.time.OffsetDateTime createdAt) {
    }
}
