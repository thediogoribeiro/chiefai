package com.chiefai.personalagent.action.application;

import com.chiefai.personalagent.action.domain.RiskLevel;
import com.chiefai.personalagent.expenses.application.ExpenseAnalysisService;
import com.chiefai.personalagent.expenses.application.ExpenseIngestionService;
import com.chiefai.personalagent.expenses.application.FinanceDashboardService;
import com.chiefai.personalagent.expenses.application.FinanceExpenseFilter;
import com.chiefai.personalagent.expenses.domain.ExpenseCategory;
import com.chiefai.personalagent.expenses.domain.ExpenseOwner;
import com.chiefai.personalagent.expenses.domain.ExpenseStatus;
import com.chiefai.personalagent.health.application.HealthSnapshotService;
import com.chiefai.personalagent.integration.gmail.GmailConnector;
import com.chiefai.personalagent.shared.Jsonb;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import com.chiefai.personalagent.task.application.PersonalTaskService;
import com.chiefai.personalagent.task.domain.TaskPriority;
import com.chiefai.personalagent.task.domain.TaskStatus;
import com.chiefai.personalagent.system.application.SystemRuntimeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Component
public class ToolRegistry {
    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

    public ToolRegistry(PersonalTaskService taskService, HealthSnapshotService healthService,
                        GmailConnector gmailConnector, ExpenseIngestionService expenseIngestionService,
                        ExpenseAnalysisService expenseAnalysisService, FinanceDashboardService financeDashboardService,
                        PersonalAgentProperties properties, SystemRuntimeService system) {
        register("system.get_status", "Consulta estado da aplicação.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(system.status())));
        register("system.get_version", "Consulta o commit instalado.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(system.version())));
        register("system.get_update_status", "Consulta o último pedido de atualização.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(system.latest())));
        register("system.request_update", "Enfileira atualização aprovada; sem argumentos.", RiskLevel.EXTERNAL_WRITE, true,
                (args, key) -> {
                    if (args == null || !args.isObject() || !args.isEmpty() || key == null || !key.startsWith("action:"))
                        return ToolExecutionResult.failure("system.request_update requires an approved action and no arguments");
                    var request = system.enqueueApproved(UUID.fromString(key.substring(7)));
                    return request == null ? ToolExecutionResult.failure("Updates are disabled or another update is active")
                            : ToolExecutionResult.success(Jsonb.value(request));
                });
        register("internal.ask_clarification", "Cria uma pergunta de clarificação interna.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(Map.of("question", text(args, "question", "Pode clarificar?")))));
        register("personal_tasks.create", "Cria uma tarefa pessoal interna.", RiskLevel.INTERNAL_WRITE, true,
                (args, key) -> {
                    String title = text(args, "title", "");
                    if (title.isBlank()) {
                        return ToolExecutionResult.failure("personal_tasks.create requires title");
                    }
                    LocalDate due = args.hasNonNull("dueDate") ? LocalDate.parse(args.get("dueDate").asText()) : null;
                    TaskPriority priority = args.hasNonNull("priority") ? TaskPriority.valueOf(args.get("priority").asText()) : TaskPriority.NORMAL;
                    var task = taskService.create(title, text(args, "description", null), priority, due, null);
                    return ToolExecutionResult.success(Jsonb.value(Map.of("taskId", task.id().toString(), "status", task.status())));
                });
        register("personal_tasks.complete", "Conclui uma tarefa pessoal interna.", RiskLevel.INTERNAL_WRITE, true,
                (args, key) -> {
                    if (!args.hasNonNull("taskId")) {
                        return ToolExecutionResult.failure("personal_tasks.complete requires taskId");
                    }
                    var task = taskService.complete(UUID.fromString(args.get("taskId").asText()));
                    return ToolExecutionResult.success(Jsonb.value(Map.of("taskId", task.id().toString(), "status", task.status())));
                });
        register("tasks.list", "Lista tarefas pessoais persistidas.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(Map.of(
                        "tasks", taskService.list(enumOrNull(args, "status", TaskStatus.class))))));
        register("health.save_metrics", "Guarda métricas diárias de saúde internas.", RiskLevel.INTERNAL_WRITE, true,
                (args, key) -> {
                    LocalDate date = args.hasNonNull("date") ? LocalDate.parse(args.get("date").asText()) : LocalDate.now();
                    String source = text(args, "source", "MANUAL");
                    var saved = healthService.upsert(date, source, args);
                    return ToolExecutionResult.success(Jsonb.value(Map.of("snapshotId", saved.id().toString())));
                });
        register("health.generate_report", "Gera um relatório objetivo simples de saúde.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(Map.of("disclaimer", HealthSnapshotService.DISCLAIMER))));
        register("health.get_snapshots", "Consulta snapshots de saúde persistidos para um intervalo.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(Map.of(
                        "snapshots", healthService.list(date(args, "from"), date(args, "to")),
                        "disclaimer", HealthSnapshotService.DISCLAIMER))));
        register("health.get_trends", "Consulta tendências de saúde calculadas pelo backend.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(healthService.trends(date(args, "from"), date(args, "to")))));
        register("oura.read_metrics", "Fake: lê métricas Oura quando integração real não está configurada.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.failure("Oura real integration is disabled in this MVP"));
        register("teams.search_messages", "Fake: pesquisa mensagens Teams.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.failure("Teams real integration is disabled in this MVP"));
        register("calendar.read_events", "Fake: lê eventos de calendário.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.failure("Calendar real integration is disabled in this MVP"));
        register("gmail.create_draft", "Fake: cria rascunho Gmail.", RiskLevel.EXTERNAL_WRITE, true,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(Map.of("fake", true, "draftId", "fake-gmail-draft"))));
        register("trello.create_card", "Fake: cria cartão Trello.", RiskLevel.EXTERNAL_WRITE, true,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(Map.of("fake", true, "cardId", "fake-trello-card"))));
        register("gmail.read_labeled_expenses", "Lê apenas mensagens Gmail com a etiqueta de despesas configurada.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(Map.of(
                        "label", properties.expenses().gmail().inputLabel(),
                        "messages", gmailConnector.findMessagesByLabel(properties.expenses().gmail().inputLabel(), 25).size()))));
        register("expenses.upsert", "Processa mensagens Gmail etiquetadas e cria despesas idempotentes.", RiskLevel.INTERNAL_WRITE, true,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(Map.of("processed", expenseIngestionService.pollOnce()))));
        register("expenses.generate_monthly_analysis", "Gera análise mensal objetiva de despesas no backend.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(expenseAnalysisService.trends(java.time.YearMonth.now().minusMonths(11), java.time.YearMonth.now()))));
        register("finance.get_dashboard", "Consulta o dashboard financeiro calculado pelo backend.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(financeDashboardService.dashboard(financeFilter(args)))));
        register("finance.search_expenses", "Pesquisa despesas persistidas com filtros seguros.", RiskLevel.READ_ONLY, false,
                (args, key) -> ToolExecutionResult.success(Jsonb.value(financeDashboardService.expenses(financeFilter(args),
                        intValue(args, "page", 0), intValue(args, "size", 10), text(args, "sort", "documentDate,desc")))));
    }

    private void register(String name, String description, RiskLevel riskLevel, boolean writes, ToolHandler handler) {
        ObjectNode schema = argumentSchema(name);
        tools.put(name, new RegisteredTool(new ToolDefinition(name, description, schema, riskLevel, writes), handler));
    }

    public boolean exists(String name) {
        return tools.containsKey(name);
    }

    public ToolDefinition definition(String name) {
        RegisteredTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        return tool.definition();
    }

    public Collection<ToolDefinition> definitionsFor(Collection<String> names) {
        return names.stream().map(this::definition).toList();
    }

    ToolExecutionResult execute(String name, JsonNode arguments, String idempotencyKey) {
        RegisteredTool tool = tools.get(name);
        if (tool == null) {
            return ToolExecutionResult.failure("Unknown tool: " + name);
        }
        JsonNode input = arguments == null ? Jsonb.object() : arguments;
        if (!validArguments(input, tool.definition().argumentSchema())) return ToolExecutionResult.failure("Invalid tool arguments");
        return tool.handler().execute(input, idempotencyKey);
    }

    private static ObjectNode argumentSchema(String name) {
        Map<String, String> fields = switch (name) {
            case "internal.ask_clarification" -> Map.of("question", "string");
            case "personal_tasks.create" -> Map.of("title", "string", "description", "string?", "priority", "string?", "dueDate", "string?");
            case "personal_tasks.complete" -> Map.of("taskId", "string");
            case "tasks.list" -> Map.of("status", "string?");
            case "health.save_metrics" -> Map.of("date", "string?", "source", "string?", "sleepScore", "integer?", "readinessScore", "integer?",
                    "activityScore", "integer?", "restingHeartRate", "number?", "averageHrv", "number?", "sleepDurationMinutes", "integer?");
            case "health.get_snapshots", "health.get_trends", "calendar.read_events" -> Map.of("from", "string?", "to", "string?");
            case "teams.search_messages" -> Map.of("query", "string?");
            case "gmail.create_draft" -> Map.of("to", "string?", "subject", "string?", "body", "string?");
            case "trello.create_card" -> Map.of("title", "string", "description", "string?");
            case "finance.get_dashboard", "finance.search_expenses" -> financeFields(name);
            default -> Map.of();
        };
        ObjectNode schema = Jsonb.object();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        var required = schema.putArray("required");
        fields.forEach((field, type) -> {
            ObjectNode property = properties.putObject(field);
            if (type.endsWith("?")) property.putArray("type").add(type.substring(0, type.length() - 1)).add("null");
            else property.put("type", type);
            required.add(field);
        });
        return schema;
    }

    private static Map<String, String> financeFields(String tool) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String field : List.of("from", "to", "owner", "category", "subcategory", "supplier", "status", "search")) fields.put(field, "string?");
        fields.put("minAmount", "number?"); fields.put("maxAmount", "number?");
        if ("finance.search_expenses".equals(tool)) {
            fields.put("page", "integer?"); fields.put("size", "integer?"); fields.put("sort", "string?");
        }
        return fields;
    }

    private static boolean validArguments(JsonNode input, JsonNode schema) {
        if (!input.isObject()) return false;
        JsonNode properties = schema.path("properties");
        var names = input.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            if (!properties.has(field)) return false;
            JsonNode value = input.get(field);
            JsonNode types = properties.path(field).path("type");
            List<String> permitted = types.isArray() ? Jsonb.MAPPER.convertValue(types, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}) : List.of(types.asText());
            boolean valid = permitted.stream().anyMatch(type -> switch (type) {
                case "null" -> value.isNull();
                case "string" -> value.isTextual();
                case "integer" -> value.isIntegralNumber();
                case "number" -> value.isNumber();
                default -> false;
            });
            if (!valid) return false;
        }
        var fields = properties.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (field.getValue().path("type").isTextual() && !input.hasNonNull(field.getKey())) return false;
        }
        return true;
    }

    private static String text(JsonNode args, String field, String defaultValue) {
        return args != null && args.hasNonNull(field) ? args.get(field).asText() : defaultValue;
    }

    private static int intValue(JsonNode args, String field, int defaultValue) {
        return args != null && args.hasNonNull(field) ? args.get(field).asInt() : defaultValue;
    }

    private static LocalDate date(JsonNode args, String field) {
        return args != null && args.hasNonNull(field) ? LocalDate.parse(args.get(field).asText()) : null;
    }

    private static BigDecimal decimal(JsonNode args, String field) {
        return args != null && args.hasNonNull(field) ? args.get(field).decimalValue() : null;
    }

    private static <E extends Enum<E>> E enumOrNull(JsonNode args, String field, Class<E> enumType) {
        if (args == null || !args.hasNonNull(field) || args.get(field).asText().isBlank()) {
            return null;
        }
        return Enum.valueOf(enumType, args.get(field).asText().toUpperCase(Locale.ROOT));
    }

    private static FinanceExpenseFilter financeFilter(JsonNode args) {
        return new FinanceExpenseFilter(
                date(args, "from"),
                date(args, "to"),
                enumOrNull(args, "owner", ExpenseOwner.class),
                enumOrNull(args, "category", ExpenseCategory.class),
                text(args, "subcategory", null),
                text(args, "supplier", null),
                enumOrNull(args, "status", ExpenseStatus.class),
                decimal(args, "minAmount"),
                decimal(args, "maxAmount"),
                text(args, "search", null));
    }

    private record RegisteredTool(ToolDefinition definition, ToolHandler handler) {
    }
}
