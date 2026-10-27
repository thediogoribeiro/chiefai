package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.domain.ExpenseCategory;
import com.chiefai.personalagent.expenses.infrastructure.ExpenseCategoryRuleEntity;
import com.chiefai.personalagent.expenses.infrastructure.ExpenseCategoryRuleJpaRepository;
import com.chiefai.personalagent.integration.openai.LanguageModelGateway;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ExpenseCategoryService {
    private final ExpenseCategoryRuleJpaRepository rules;
    private final LanguageModelGateway languageModelGateway;

    public ExpenseCategoryService(ExpenseCategoryRuleJpaRepository rules, LanguageModelGateway languageModelGateway) {
        this.rules = rules;
        this.languageModelGateway = languageModelGateway;
    }

    public ExpenseClassification classify(String supplier, String description) {
        String safeSupplier = normalize(supplier);
        String safeDescription = normalize(description);
        for (ExpenseCategoryRuleEntity rule : rules.findByEnabledTrueOrderByPriorityAsc()) {
            if (matches(rule.getSupplierPattern(), safeSupplier) || matches(rule.getDescriptionPattern(), safeDescription)) {
                return new ExpenseClassification(rule.getCategory(), rule.getSubcategory(), Boolean.TRUE.equals(rule.getRecurring()), 0.95, "RULE");
            }
        }
        ExpenseExtraction extraction = languageModelGateway.extractExpense((supplier == null ? "" : supplier) + "\n" + (description == null ? "" : description));
        return new ExpenseClassification(extraction.category() == null ? ExpenseCategory.OTHER : extraction.category(),
                extraction.subcategory() == null ? "outros" : extraction.subcategory(),
                Boolean.TRUE.equals(extraction.recurring()), extraction.confidence(), "LLM_STRUCTURED_FAKE");
    }

    private boolean matches(String pattern, String value) {
        return pattern != null && !pattern.isBlank() && Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(value).find();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
