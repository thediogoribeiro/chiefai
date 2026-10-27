package com.chiefai.personalagent.integration.openai;

import com.chiefai.personalagent.action.application.ToolDefinition;
import com.chiefai.personalagent.agent.domain.AgentDefinition;
import com.chiefai.personalagent.expenses.application.ExpenseExtraction;
import com.chiefai.personalagent.expenses.domain.ExpenseCategory;
import com.chiefai.personalagent.expenses.domain.ExpenseOwner;
import com.chiefai.personalagent.shared.Jsonb;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Primary
@ConditionalOnProperty(prefix = "personal-agent.openai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class FakeLanguageModelGateway implements LanguageModelGateway {
    private final AtomicInteger routeCalls = new AtomicInteger();
    private final AtomicInteger specialistCalls = new AtomicInteger();
    private final AtomicInteger expenseExtractionCalls = new AtomicInteger();

    @Override
    public RouteResult route(String eventText, String managerInstructions, String routingSchema, Collection<AgentDefinition> specialists, int maxRoutes) {
        routeCalls.incrementAndGet();
        String lower = eventText == null ? "" : eventText.toLowerCase(Locale.ROOT);
        List<RouteResult.Route> routes = new ArrayList<>();
        if (containsAny(lower, "rucodel", "rucopi", "rucofi", "rucoplan", "reunião", "reuniao", "produção", "producao")) {
            routes.add(new RouteResult.Route("rucodel", eventText, 0.88, "Fake manager found work/Rucodel terms"));
        }
        if (containsAny(lower, "sono", "dormi", "dormir", "readiness", "oura", "hrv", "saúde", "saude")) {
            routes.add(new RouteResult.Route("health", eventText, 0.86, "Fake manager found health terms"));
        }
        if (routes.isEmpty() || containsAny(lower, "tarefa pessoal", "comprar", "casa", "pessoal", "gasto", "gastos", "despesa", "despesas", "finança", "financas", "financeiro")) {
            routes.add(new RouteResult.Route("personal", eventText, 0.7, "Fake manager defaulted to personal organization"));
        }
        return new RouteResult(routes.stream().limit(maxRoutes).toList(), false, null);
    }

    @Override
    public SpecialistResult runSpecialist(AgentDefinition agent, String instructions, String segment, String knowledgeContext, Collection<ToolDefinition> tools, String schema, String history) {
        specialistCalls.incrementAndGet();
        String lower = segment == null ? "" : segment.toLowerCase(Locale.ROOT);
        List<SpecialistResult.ActionCall> actions = new ArrayList<>();
        if ("rucodel".equals(agent.id()) && containsAny(lower, "tarefa", "cartão", "cartao", "trello", "reunião", "reuniao")) {
            ObjectNode args = Jsonb.object();
            args.put("title", firstLine(segment, "Tarefa Rucodel"));
            args.put("description", segment);
            actions.add(new SpecialistResult.ActionCall("trello.create_card", args, "Criar cartão para acompanhar o assunto da Rucodel", true));
        }
        if ("personal".equals(agent.id()) && containsAny(lower, "tarefa", "lembrar", "comprar")) {
            ObjectNode args = Jsonb.object();
            args.put("title", firstLine(segment, "Tarefa pessoal"));
            args.put("description", segment);
            args.put("priority", "NORMAL");
            actions.add(new SpecialistResult.ActionCall("personal_tasks.create", args, "Criar tarefa pessoal interna", false));
        }
        if ("personal".equals(agent.id()) && containsAny(lower, "tarefa aberta", "tarefas abertas", "lista tarefas", "minhas tarefas")) {
            actions.add(new SpecialistResult.ActionCall("tasks.list", Jsonb.object(), "Consultar tarefas pessoais persistidas", false));
        }
        if ("personal".equals(agent.id()) && containsAny(lower, "finance", "finança", "financas", "gasto", "gastos", "despesa", "despesas", "categoria")) {
            actions.add(new SpecialistResult.ActionCall("finance.get_dashboard", Jsonb.object(), "Consultar agregados financeiros persistidos", false));
        }
        if ("health".equals(agent.id()) && containsAny(lower, "sleep", "sono", "dormi", "dormir", "readiness", "oura", "hrv")) {
            ObjectNode args = Jsonb.object();
            args.put("date", LocalDate.now().toString());
            args.put("source", "OURA");
            actions.add(new SpecialistResult.ActionCall("health.get_trends", Jsonb.object(), "Consultar tendências de saúde persistidas", false));
            actions.add(new SpecialistResult.ActionCall("health.generate_report", Jsonb.object(), "Gerar síntese não diagnóstica", false));
        }
        return new SpecialistResult("Processado por fake " + agent.id(), List.of(), actions,
                "Recebido e processado por " + agent.id() + ".", 0.8,
                "health".equals(agent.id()) ? List.of("Não constitui diagnóstico médico.") : List.of());
    }

    @Override
    public ExpenseExtraction extractExpense(String trustedText) {
        expenseExtractionCalls.incrementAndGet();
        String lower = trustedText == null ? "" : trustedText.toLowerCase(Locale.ROOT);
        ExpenseCategory category = containsAny(lower, "streaming", "software", "cloud") ? ExpenseCategory.SUBSCRIPTION : ExpenseCategory.OTHER;
        return new ExpenseExtraction("Fornecedor ambíguo", null, LocalDate.now(), null, "EUR", null,
                ExpenseOwner.UNKNOWN, category, category == ExpenseCategory.SUBSCRIPTION ? "software" : "outros", category == ExpenseCategory.SUBSCRIPTION,
                0.62, List.of("classificação fake estruturada"));
    }

    public int routeCalls() {
        return routeCalls.get();
    }

    public int specialistCalls() {
        return specialistCalls.get();
    }

    public int expenseExtractionCalls() {
        return expenseExtractionCalls.get();
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static String firstLine(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String value = text.lines().findFirst().orElse(fallback).trim();
        return value.length() > 120 ? value.substring(0, 120) : value;
    }
}
