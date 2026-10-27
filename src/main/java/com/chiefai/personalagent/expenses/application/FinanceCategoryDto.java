package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.domain.ExpenseCategory;

import java.util.List;

public record FinanceCategoryDto(ExpenseCategory category, List<String> subcategories) {
}
