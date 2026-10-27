package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.domain.ExpenseCategory;
import com.chiefai.personalagent.expenses.domain.ExpenseOwner;
import com.chiefai.personalagent.expenses.infrastructure.ExpenseRecordEntity;
import com.chiefai.personalagent.expenses.infrastructure.ExpenseRecordJpaRepository;
import com.chiefai.personalagent.integration.google.sheets.MonthlyExpenseSummaryRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

@Service
public class ExpenseAnalysisService {
    private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");
    private final ExpenseRecordJpaRepository expenses;

    public ExpenseAnalysisService(ExpenseRecordJpaRepository expenses) {
        this.expenses = expenses;
    }

    @Transactional(readOnly = true)
    public List<MonthlyExpenseSummaryRow> monthlySummaryRows() {
        YearMonth current = YearMonth.now(LISBON);
        YearMonth start = current.minusMonths(11);
        return monthlySummary(start, current);
    }

    @Transactional(readOnly = true)
    public List<MonthlyExpenseSummaryRow> monthlySummary(YearMonth from, YearMonth to) {
        List<MonthlyExpenseSummaryRow> rows = new ArrayList<>();
        BigDecimal previous = null;
        YearMonth cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate start = cursor.atDay(1);
            LocalDate end = cursor.atEndOfMonth();
            List<ExpenseRecordEntity> monthRows = expenses.findByDocumentDateBetweenOrderByDocumentDateAsc(start, end);
            BigDecimal total = sum(monthRows, null, null);
            BigDecimal variation = previous == null ? BigDecimal.ZERO : total.subtract(previous);
            rows.add(new MonthlyExpenseSummaryRow(cursor, total, sum(monthRows, ExpenseCategory.FIXED_COST, null),
                    sum(monthRows, ExpenseCategory.SUBSCRIPTION, null), sum(monthRows, ExpenseCategory.VARIABLE_PURCHASE, null),
                    sum(monthRows, ExpenseCategory.OTHER, null), sum(monthRows, null, ExpenseOwner.DIOGO), sum(monthRows, null, ExpenseOwner.MARIA),
                    monthRows.size(), variation));
            previous = total;
            cursor = cursor.plusMonths(1);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> trends(YearMonth from, YearMonth to) {
        return Map.of("timezone", LISBON.toString(), "months", monthlySummary(from, to), "generatedBy", "backend");
    }

    private BigDecimal sum(List<ExpenseRecordEntity> rows, ExpenseCategory category, ExpenseOwner owner) {
        return rows.stream()
                .filter(row -> row.getGrossAmount() != null)
                .filter(row -> category == null || row.getCategory() == category)
                .filter(row -> owner == null || row.getOwner() == owner)
                .map(ExpenseRecordEntity::getGrossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
