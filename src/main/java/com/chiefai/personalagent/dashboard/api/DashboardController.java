package com.chiefai.personalagent.dashboard.api;

import com.chiefai.personalagent.expenses.application.*;
import com.chiefai.personalagent.health.application.HealthSnapshotService;
import com.chiefai.personalagent.integration.ConnectorRegistry;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final FinanceDashboardService finance;
    private final HealthSnapshotService health;
    private final ConnectorRegistry connectors;

    public DashboardController(FinanceDashboardService finance, HealthSnapshotService health, ConnectorRegistry connectors) {
        this.finance = finance;
        this.health = health;
        this.connectors = connectors;
    }

    @GetMapping("/modules")
    List<DashboardModuleDto> modules() {
        return Stream.of(
                new DashboardModuleDto("overview", "Visão geral", "/", "layout-dashboard", true, null),
                new DashboardModuleDto("finance", "Finanças", "/finance", "wallet", true, null),
                new DashboardModuleDto("health", "Saúde", "/health", "heart-pulse", true, null),
                new DashboardModuleDto("settings", "Definições", "/settings", "settings", false, "admin"))
                .filter(DashboardModuleDto::enabled)
                .toList();
    }

    @GetMapping("/overview")
    Map<String, Object> overview() {
        YearMonth current = YearMonth.now(FinanceDashboardService.LISBON);
        FinanceDashboardDto financeSummary = finance.dashboard(new FinanceExpenseFilter(current.atDay(1), current.atEndOfMonth(), null, null, null, null, null, null, null, null));
        LocalDate today = LocalDate.now(FinanceDashboardService.LISBON);
        var latestHealth = health.list(today.minusDays(14), today).stream().reduce((first, second) -> second).orElse(null);
        return Map.of(
                "finance", financeSummary,
                "healthLatest", latestHealth == null ? Map.of("empty", true, "disclaimer", HealthSnapshotService.DISCLAIMER) : latestHealth,
                "connectors", connectors.status(),
                "generatedAt", java.time.OffsetDateTime.now());
    }

    public record DashboardModuleDto(String id, String label, String route, String icon, boolean enabled,
                                     String requiredPermission) {
    }
}
