package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.domain.ExpenseCategory;
import com.chiefai.personalagent.expenses.domain.ExpenseOwner;
import com.chiefai.personalagent.expenses.domain.ExpenseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceExpenseFilter(LocalDate from, LocalDate to, ExpenseOwner owner, ExpenseCategory category,
                                   String subcategory, String supplier, ExpenseStatus status, BigDecimal minAmount,
                                   BigDecimal maxAmount, String search) {
    public FinanceExpenseFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("minAmount must be lower than or equal to maxAmount");
        }
    }
}
