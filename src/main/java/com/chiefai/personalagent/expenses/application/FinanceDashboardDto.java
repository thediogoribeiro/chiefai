package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.domain.ExpenseCategory;
import com.chiefai.personalagent.expenses.domain.ExpenseOwner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

public record FinanceDashboardDto(PeriodDto period, TotalsDto totals, List<MonthlyPointDto> monthlyEvolution,
                                  List<CategoryPointDto> byCategory, List<OwnerPointDto> byOwner,
                                  List<SupplierPointDto> topSuppliers, List<CategoryPointDto> typeBreakdown,
                                  ComparisonDto previousPeriodComparison, long reviewPendingCount,
                                  OffsetDateTime lastUpdatedAt) {
    public record PeriodDto(LocalDate from, LocalDate to, String timezone) {
    }

    public record TotalsDto(BigDecimal total, BigDecimal fixedCosts, BigDecimal subscriptions,
                            BigDecimal variablePurchases, BigDecimal other, BigDecimal diogo,
                            BigDecimal maria, long expenseCount, BigDecimal previousPeriodVariationPercentage) {
    }

    public record MonthlyPointDto(YearMonth month, BigDecimal total, long expenseCount) {
    }

    public record CategoryPointDto(ExpenseCategory category, String subcategory, BigDecimal total, long expenseCount) {
    }

    public record OwnerPointDto(ExpenseOwner owner, BigDecimal total, long expenseCount) {
    }

    public record SupplierPointDto(String supplier, BigDecimal total, long expenseCount) {
    }

    public record ComparisonDto(BigDecimal currentTotal, BigDecimal previousTotal, BigDecimal variation,
                                BigDecimal variationPercentage) {
    }
}
