package com.chiefai.personalagent.expenses.application;

import java.util.List;

public record FinanceExpenseDetailDto(ExpenseDto expense, String safeSourceReference,
                                      List<ExpenseService.ExpenseAuditDto> history) {
}
