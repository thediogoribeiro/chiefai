package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.domain.ExpenseCategory;
import com.chiefai.personalagent.expenses.domain.ExpenseOwner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExpenseExtraction(
        String supplierName,
        String invoiceOrReceiptNumber,
        LocalDate documentDate,
        LocalDate dueDate,
        String currency,
        BigDecimal grossAmount,
        ExpenseOwner owner,
        ExpenseCategory category,
        String subcategory,
        Boolean recurring,
        double confidence,
        List<String> evidence
) {
}
