package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.domain.*;
import com.chiefai.personalagent.expenses.infrastructure.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FinanceDashboardService {
    public static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");
    private static final Set<ExpenseStatus> DEFAULT_TOTAL_STATUSES = Set.of(ExpenseStatus.EXTRACTED, ExpenseStatus.APPROVED, ExpenseStatus.SYNCED);
    private final ExpenseRecordJpaRepository expenses;
    private final ExpenseCategoryRuleJpaRepository rules;
    private final FinanceDashboardUpdateJpaRepository dashboardUpdates;
    private final ExpenseService expenseService;

    public FinanceDashboardService(ExpenseRecordJpaRepository expenses, ExpenseCategoryRuleJpaRepository rules,
                                   FinanceDashboardUpdateJpaRepository dashboardUpdates, ExpenseService expenseService) {
        this.expenses = expenses;
        this.rules = rules;
        this.dashboardUpdates = dashboardUpdates;
        this.expenseService = expenseService;
    }

    @Transactional(readOnly = true)
    public FinanceDashboardDto dashboard(FinanceExpenseFilter filter) {
        FinanceExpenseFilter normalized = normalizePeriod(filter);
        List<ExpenseRecordEntity> rows = expenses.findAll(spec(normalized, false), Sort.by("documentDate").ascending().and(Sort.by("receivedAt").ascending()));
        LocalDate from = normalized.from();
        LocalDate to = normalized.to();
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        FinanceExpenseFilter previousFilter = new FinanceExpenseFilter(from.minusDays(days), from.minusDays(1), normalized.owner(),
                normalized.category(), normalized.subcategory(), normalized.supplier(), normalized.status(),
                normalized.minAmount(), normalized.maxAmount(), normalized.search());
        BigDecimal previousTotal = sum(expenses.findAll(spec(previousFilter, false)));
        BigDecimal currentTotal = sum(rows);
        BigDecimal variation = currentTotal.subtract(previousTotal);
        BigDecimal variationPct = previousTotal.compareTo(BigDecimal.ZERO) == 0 ? null
                : variation.multiply(BigDecimal.valueOf(100)).divide(previousTotal, 2, RoundingMode.HALF_UP);

        return new FinanceDashboardDto(
                new FinanceDashboardDto.PeriodDto(from, to, LISBON.toString()),
                totals(rows, variationPct),
                monthlyEvolution(rows, from, to),
                byCategory(rows),
                byOwner(rows),
                topSuppliers(rows),
                typeBreakdown(rows),
                new FinanceDashboardDto.ComparisonDto(currentTotal, previousTotal, variation, variationPct),
                expenses.countByStatus(ExpenseStatus.REVIEW_REQUIRED),
                dashboardUpdates.findFirstByOrderByCreatedAtDesc().map(FinanceDashboardUpdateEntity::getCreatedAt)
                        .or(() -> expenses.findLastUpdatedAt()).orElse(null));
    }

    @Transactional(readOnly = true)
    public ExpensePageDto expenses(FinanceExpenseFilter filter, int page, int size, String sort) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Sort springSort = toSort(sort);
        Page<ExpenseRecordEntity> result = expenses.findAll(spec(normalizePeriod(filter), true), PageRequest.of(safePage, safeSize, springSort));
        return new ExpensePageDto(result.getContent().stream().map(ExpenseMapper::toDto).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), springSort.toString());
    }

    @Transactional(readOnly = true)
    public FinanceExpenseDetailDto detail(UUID id) {
        ExpenseDto dto = expenseService.get(id);
        String safeReference = dto.source().name() + ":" + Integer.toHexString(Objects.hashCode(dto.sourceMessageId()));
        return new FinanceExpenseDetailDto(dto, safeReference, expenseService.history(id));
    }

    @Transactional(readOnly = true)
    public List<ExpenseRecordEntity> exportRows(FinanceExpenseFilter filter, int maxRows) {
        return expenses.findAll(spec(normalizePeriod(filter), true), PageRequest.of(0, Math.max(1, maxRows), toSort("documentDate,asc")))
                .getContent();
    }

    @Transactional(readOnly = true)
    public long countForExport(FinanceExpenseFilter filter) {
        return expenses.count(spec(normalizePeriod(filter), true));
    }

    @Transactional(readOnly = true)
    public List<FinanceCategoryDto> categories() {
        Map<ExpenseCategory, Set<String>> values = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory category : ExpenseCategory.values()) {
            values.put(category, new TreeSet<>());
        }
        for (ExpenseCategoryRuleEntity rule : rules.findByEnabledTrueOrderByPriorityAsc()) {
            values.get(rule.getCategory()).add(rule.getSubcategory());
        }
        for (ExpenseRecordEntity row : expenses.findAll()) {
            if (row.getSubcategory() != null && !row.getSubcategory().isBlank()) {
                values.get(row.getCategory()).add(row.getSubcategory());
            }
        }
        return values.entrySet().stream()
                .map(entry -> new FinanceCategoryDto(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    public FinanceExpenseFilter normalizePeriod(FinanceExpenseFilter filter) {
        LocalDate today = LocalDate.now(LISBON);
        LocalDate to = filter.to() == null ? today : filter.to();
        LocalDate from = filter.from() == null ? YearMonth.from(to).atDay(1) : filter.from();
        return new FinanceExpenseFilter(from, to, filter.owner(), filter.category(), blankToNull(filter.subcategory()),
                blankToNull(filter.supplier()), filter.status(), filter.minAmount(), filter.maxAmount(), blankToNull(filter.search()));
    }

    private Specification<ExpenseRecordEntity> spec(FinanceExpenseFilter filter, boolean includeAllStatusesByDefault) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("documentDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("documentDate"), filter.to()));
            }
            if (filter.owner() != null) {
                predicates.add(cb.equal(root.get("owner"), filter.owner()));
            }
            if (filter.category() != null) {
                predicates.add(cb.equal(root.get("category"), filter.category()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            } else if (!includeAllStatusesByDefault) {
                predicates.add(root.get("status").in(DEFAULT_TOTAL_STATUSES));
            }
            if (filter.subcategory() != null) {
                predicates.add(cb.like(cb.lower(root.get("subcategory")), contains(filter.subcategory())));
            }
            if (filter.supplier() != null) {
                predicates.add(cb.like(cb.lower(root.get("supplierName")), contains(filter.supplier())));
            }
            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("grossAmount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("grossAmount"), filter.maxAmount()));
            }
            if (filter.search() != null) {
                String needle = contains(filter.search());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("supplierName")), needle),
                        cb.like(cb.lower(root.get("description")), needle),
                        cb.like(cb.lower(root.get("invoiceOrReceiptNumber")), needle),
                        cb.like(cb.lower(root.get("subcategory")), needle)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private FinanceDashboardDto.TotalsDto totals(List<ExpenseRecordEntity> rows, BigDecimal variationPct) {
        return new FinanceDashboardDto.TotalsDto(sum(rows),
                sum(rows, row -> row.getCategory() == ExpenseCategory.FIXED_COST),
                sum(rows, row -> row.getCategory() == ExpenseCategory.SUBSCRIPTION),
                sum(rows, row -> row.getCategory() == ExpenseCategory.VARIABLE_PURCHASE),
                sum(rows, row -> row.getCategory() == ExpenseCategory.OTHER),
                sum(rows, row -> row.getOwner() == ExpenseOwner.DIOGO),
                sum(rows, row -> row.getOwner() == ExpenseOwner.MARIA),
                rows.size(), variationPct);
    }

    private List<FinanceDashboardDto.MonthlyPointDto> monthlyEvolution(List<ExpenseRecordEntity> rows, LocalDate from, LocalDate to) {
        Map<YearMonth, List<ExpenseRecordEntity>> byMonth = rows.stream()
                .filter(row -> row.getDocumentDate() != null)
                .collect(Collectors.groupingBy(row -> YearMonth.from(row.getDocumentDate()), TreeMap::new, Collectors.toList()));
        List<FinanceDashboardDto.MonthlyPointDto> points = new ArrayList<>();
        YearMonth cursor = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        while (!cursor.isAfter(end)) {
            List<ExpenseRecordEntity> monthRows = byMonth.getOrDefault(cursor, List.of());
            points.add(new FinanceDashboardDto.MonthlyPointDto(cursor, sum(monthRows), monthRows.size()));
            cursor = cursor.plusMonths(1);
        }
        return points;
    }

    private List<FinanceDashboardDto.CategoryPointDto> byCategory(List<ExpenseRecordEntity> rows) {
        return grouped(rows, ExpenseRecordEntity::getCategory).entrySet().stream()
                .map(entry -> new FinanceDashboardDto.CategoryPointDto(entry.getKey(), null, sum(entry.getValue()), entry.getValue().size()))
                .toList();
    }

    private List<FinanceDashboardDto.CategoryPointDto> typeBreakdown(List<ExpenseRecordEntity> rows) {
        return rows.stream().collect(Collectors.groupingBy(row -> row.getCategory() + "|" + nullToOther(row.getSubcategory())))
                .entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("\\|", 2);
                    return new FinanceDashboardDto.CategoryPointDto(ExpenseCategory.valueOf(parts[0]), parts[1], sum(entry.getValue()), entry.getValue().size());
                })
                .sorted(Comparator.comparing(FinanceDashboardDto.CategoryPointDto::total).reversed())
                .toList();
    }

    private List<FinanceDashboardDto.OwnerPointDto> byOwner(List<ExpenseRecordEntity> rows) {
        return grouped(rows, ExpenseRecordEntity::getOwner).entrySet().stream()
                .map(entry -> new FinanceDashboardDto.OwnerPointDto(entry.getKey(), sum(entry.getValue()), entry.getValue().size()))
                .toList();
    }

    private List<FinanceDashboardDto.SupplierPointDto> topSuppliers(List<ExpenseRecordEntity> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(row -> nullToOther(row.getSupplierName())))
                .entrySet().stream()
                .map(entry -> new FinanceDashboardDto.SupplierPointDto(entry.getKey(), sum(entry.getValue()), entry.getValue().size()))
                .sorted(Comparator.comparing(FinanceDashboardDto.SupplierPointDto::total).reversed())
                .limit(10)
                .toList();
    }

    private <T> Map<T, List<ExpenseRecordEntity>> grouped(List<ExpenseRecordEntity> rows, Function<ExpenseRecordEntity, T> classifier) {
        return rows.stream().collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.toList()));
    }

    private BigDecimal sum(List<ExpenseRecordEntity> rows) {
        return sum(rows, row -> true);
    }

    private BigDecimal sum(List<ExpenseRecordEntity> rows, java.util.function.Predicate<ExpenseRecordEntity> predicate) {
        return rows.stream().filter(predicate).map(ExpenseRecordEntity::getGrossAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Sort toSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "documentDate").and(Sort.by(Sort.Direction.DESC, "receivedAt"));
        }
        String[] parts = sort.split(",", 2);
        Set<String> allowed = Set.of("documentDate", "receivedAt", "supplierName", "grossAmount", "owner", "category", "status");
        String property = allowed.contains(parts[0]) ? parts[0] : "documentDate";
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    private String contains(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).trim().replace("%", "\\%") + "%";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToOther(String value) {
        return value == null || value.isBlank() ? "outros" : value;
    }
}
