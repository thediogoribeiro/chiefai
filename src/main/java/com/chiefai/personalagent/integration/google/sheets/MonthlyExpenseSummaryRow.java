package com.chiefai.personalagent.integration.google.sheets;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthlyExpenseSummaryRow(YearMonth month, BigDecimal total, BigDecimal fixedCosts, BigDecimal subscriptions,
                                       BigDecimal variablePurchases, BigDecimal other, BigDecimal totalDiogo,
                                       BigDecimal totalMaria, int expenseCount, BigDecimal variationFromPreviousMonth) {
}
