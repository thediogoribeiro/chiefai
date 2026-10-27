package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.infrastructure.ExpenseRecordEntity;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FinanceExcelExportService {
    private final FinanceDashboardService dashboardService;
    private final ExpenseService expenseService;
    private final PersonalAgentProperties properties;

    public FinanceExcelExportService(FinanceDashboardService dashboardService, ExpenseService expenseService, PersonalAgentProperties properties) {
        this.dashboardService = dashboardService;
        this.expenseService = expenseService;
        this.properties = properties;
    }

    @Transactional
    public ExportedWorkbook export(FinanceExpenseFilter filter) {
        int maxRows = Math.max(1, properties.finance().export().maxRows());
        long count = dashboardService.countForExport(filter);
        if (count > maxRows) {
            throw new IllegalArgumentException("Export limit exceeded: " + count + " rows requested, maximum is " + maxRows);
        }
        FinanceExpenseFilter normalized = dashboardService.normalizePeriod(filter);
        FinanceDashboardDto dashboard = dashboardService.dashboard(normalized);
        List<ExpenseRecordEntity> rows = dashboardService.exportRows(normalized, maxRows);
        byte[] bytes = buildWorkbook(normalized, dashboard, rows);
        expenseService.logExport(count, Map.of("from", normalized.from().toString(), "to", normalized.to().toString()));
        String filename = "chiefai-financas-%s-a-%s.xlsx".formatted(normalized.from(), normalized.to());
        return new ExportedWorkbook(filename, bytes);
    }

    private byte[] buildWorkbook(FinanceExpenseFilter filter, FinanceDashboardDto dashboard, List<ExpenseRecordEntity> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00 [$EUR]"));
            CellStyle date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));

            writeSummary(workbook, dashboard, money);
            writeExpenses(workbook, rows, money, date);
            writeCategories(workbook, rows, money);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate Excel export", e);
        }
    }

    private void writeSummary(Workbook workbook, FinanceDashboardDto dashboard, CellStyle money) {
        Sheet sheet = workbook.createSheet("Resumo");
        int row = 0;
        row = keyValue(sheet, row, "Período", dashboard.period().from() + " a " + dashboard.period().to(), null);
        row = keyValue(sheet, row, "Timezone", dashboard.period().timezone(), null);
        row = keyValue(sheet, row, "Total", dashboard.totals().total(), money);
        row = keyValue(sheet, row, "Custos fixos", dashboard.totals().fixedCosts(), money);
        row = keyValue(sheet, row, "Subscrições", dashboard.totals().subscriptions(), money);
        row = keyValue(sheet, row, "Gastos variáveis", dashboard.totals().variablePurchases(), money);
        row = keyValue(sheet, row, "Outros", dashboard.totals().other(), money);
        row = keyValue(sheet, row, "Total Diogo", dashboard.totals().diogo(), money);
        row = keyValue(sheet, row, "Total Maria", dashboard.totals().maria(), money);
        row = keyValue(sheet, row, "Variação %", dashboard.totals().previousPeriodVariationPercentage(), null);
        autosize(sheet, 2);
    }

    private void writeExpenses(Workbook workbook, List<ExpenseRecordEntity> rows, CellStyle money, CellStyle date) {
        Sheet sheet = workbook.createSheet("Despesas");
        String[] headers = {"Data", "Titular", "Fornecedor", "Documento", "Descrição", "Categoria", "Subcategoria", "Tipo", "Valor", "Estado", "Origem"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        int rowNum = 1;
        for (ExpenseRecordEntity expense : rows) {
            Row row = sheet.createRow(rowNum++);
            dateCell(row, 0, expense.getDocumentDate(), date);
            row.createCell(1).setCellValue(expense.getOwner().name());
            row.createCell(2).setCellValue(safeText(expense.getSupplierName()));
            row.createCell(3).setCellValue(safeText(expense.getInvoiceOrReceiptNumber()));
            row.createCell(4).setCellValue(safeText(expense.getDescription()));
            row.createCell(5).setCellValue(expense.getCategory().name());
            row.createCell(6).setCellValue(safeText(expense.getSubcategory()));
            row.createCell(7).setCellValue(Boolean.TRUE.equals(expense.getRecurring()) ? "recorrente" : "pontual");
            moneyCell(row, 8, expense.getGrossAmount(), money);
            row.createCell(9).setCellValue(expense.getStatus().name());
            row.createCell(10).setCellValue(expense.getSource().name());
        }
        autosize(sheet, headers.length);
    }

    private void writeCategories(Workbook workbook, List<ExpenseRecordEntity> rows, CellStyle money) {
        Sheet sheet = workbook.createSheet("Categorias");
        String[] headers = {"Categoria", "Subcategoria", "Total", "Número de despesas"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        Map<String, List<ExpenseRecordEntity>> grouped = rows.stream()
                .collect(Collectors.groupingBy(row -> row.getCategory().name() + "|" + (row.getSubcategory() == null ? "" : row.getSubcategory()),
                        TreeMap::new, Collectors.toList()));
        int rowNum = 1;
        for (Map.Entry<String, List<ExpenseRecordEntity>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(parts[0]);
            row.createCell(1).setCellValue(safeText(parts.length > 1 ? parts[1] : ""));
            moneyCell(row, 2, entry.getValue().stream().map(ExpenseRecordEntity::getGrossAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add), money);
            row.createCell(3).setCellValue(entry.getValue().size());
        }
        autosize(sheet, headers.length);
    }

    private int keyValue(Sheet sheet, int rowNum, String key, Object value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(key);
        Cell cell = row.createCell(1);
        if (value instanceof BigDecimal number) {
            cell.setCellValue(number.doubleValue());
            if (style != null) {
                cell.setCellStyle(style);
            }
        } else {
            cell.setCellValue(safeText(value == null ? "" : value.toString()));
        }
        return rowNum + 1;
    }

    private void moneyCell(Row row, int index, BigDecimal amount, CellStyle money) {
        Cell cell = row.createCell(index);
        if (amount != null) {
            cell.setCellValue(amount.doubleValue());
            cell.setCellStyle(money);
        }
    }

    private void dateCell(Row row, int index, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(index);
        if (value != null) {
            cell.setCellValue(value);
            cell.setCellStyle(style);
        }
    }

    private String safeText(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.length() > 500 ? value.substring(0, 500) : value;
        return trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("-") || trimmed.startsWith("@")
                ? "'" + trimmed
                : trimmed;
    }

    private void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    public record ExportedWorkbook(String filename, byte[] bytes) {
    }
}
