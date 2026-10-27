package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.domain.ExpenseCategory;

public record ExpenseClassification(ExpenseCategory category, String subcategory, boolean recurring, double confidence, String strategy) {
}
