package com.chiefai.personalagent.expenses.api;

import com.chiefai.personalagent.expenses.application.*;
import com.chiefai.personalagent.expenses.domain.*;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {
    private final FinanceDashboardService finance;
    private final ExpenseService expenses;
    private final FinanceExcelExportService exportService;

    public FinanceController(FinanceDashboardService finance, ExpenseService expenses, FinanceExcelExportService exportService) {
        this.finance = finance;
        this.expenses = expenses;
        this.exportService = exportService;
    }

    @GetMapping("/dashboard")
    FinanceDashboardDto dashboard(FinanceQuery query) {
        return finance.dashboard(query.toFilter());
    }

    @GetMapping("/expenses")
    ExpensePageDto expenses(FinanceQuery query,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "25") int size,
                            @RequestParam(defaultValue = "documentDate,desc") String sort) {
        return finance.expenses(query.toFilter(), page, size, sort);
    }

    @GetMapping("/expenses/{id}")
    FinanceExpenseDetailDto detail(@PathVariable UUID id) {
        return finance.detail(id);
    }

    @PatchMapping("/expenses/{id}")
    ExpenseDto patch(@PathVariable UUID id, @Valid @RequestBody UpdateExpenseRequest request) {
        return expenses.correct(id, request.owner(), request.category(), request.subcategory(), request.description());
    }

    @PostMapping("/expenses/{id}/approve")
    ExpenseDto approve(@PathVariable UUID id) {
        return expenses.approve(id);
    }

    @PostMapping("/expenses/{id}/reject")
    ExpenseDto reject(@PathVariable UUID id, @Valid @RequestBody(required = false) RejectExpenseRequest request) {
        return expenses.reject(id, request == null ? "Rejected" : request.reason());
    }

    @PostMapping("/expenses/{id}/retry")
    ExpenseDto retry(@PathVariable UUID id) {
        return expenses.retry(id);
    }

    @GetMapping("/categories")
    List<FinanceCategoryDto> categories() {
        return finance.categories();
    }

    @GetMapping("/expenses/export")
    ResponseEntity<ByteArrayResource> export(FinanceQuery query) {
        FinanceExcelExportService.ExportedWorkbook workbook = exportService.export(query.toFilter());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + workbook.filename() + "\"")
                .body(new ByteArrayResource(workbook.bytes()));
    }

    public record FinanceQuery(LocalDate from, LocalDate to, ExpenseOwner owner, ExpenseCategory category,
                               String subcategory, String supplier, ExpenseStatus status, BigDecimal minAmount,
                               BigDecimal maxAmount, String search) {
        FinanceExpenseFilter toFilter() {
            return new FinanceExpenseFilter(from, to, owner, category, subcategory, supplier, status, minAmount, maxAmount, search);
        }
    }

    public record UpdateExpenseRequest(ExpenseOwner owner, ExpenseCategory category, String subcategory, String description) {
    }

    public record RejectExpenseRequest(String reason) {
    }
}
