package com.chiefai.personalagent.expenses.api;

import com.chiefai.personalagent.expenses.application.*;
import com.chiefai.personalagent.expenses.domain.ExpenseStatus;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {
    private final ExpenseService expenses;
    private final ExpenseAnalysisService analysis;

    public ExpenseController(ExpenseService expenses, ExpenseAnalysisService analysis) {
        this.expenses = expenses;
        this.analysis = analysis;
    }

    @GetMapping
    List<ExpenseDto> list(@RequestParam(required = false) ExpenseStatus status) {
        return expenses.list(status);
    }

    @GetMapping("/{id}")
    ExpenseDto get(@PathVariable UUID id) {
        return expenses.get(id);
    }

    @GetMapping("/trends")
    Map<String, Object> trends(@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to) {
        YearMonth end = to == null ? YearMonth.now(java.time.ZoneId.of("Europe/Lisbon")) : to;
        YearMonth start = from == null ? end.minusMonths(11) : from;
        return analysis.trends(start, end);
    }

    @GetMapping("/monthly-summary")
    List<com.chiefai.personalagent.integration.google.sheets.MonthlyExpenseSummaryRow> monthlySummary(@RequestParam(required = false) YearMonth from,
                                                                                                      @RequestParam(required = false) YearMonth to) {
        YearMonth end = to == null ? YearMonth.now(java.time.ZoneId.of("Europe/Lisbon")) : to;
        YearMonth start = from == null ? end.minusMonths(11) : from;
        return analysis.monthlySummary(start, end);
    }

    @PostMapping("/{id}/approve")
    ExpenseDto approve(@PathVariable UUID id) {
        return expenses.approve(id);
    }

    @PostMapping("/{id}/reject")
    ExpenseDto reject(@PathVariable UUID id, @Valid @RequestBody(required = false) RejectRequest request) {
        return expenses.reject(id, request == null ? "Rejected" : request.reason());
    }

    @PostMapping("/{id}/retry-sync")
    ExpenseDto retrySync(@PathVariable UUID id) {
        return expenses.retrySync(id);
    }

    public record RejectRequest(String reason) {
    }
}
