package com.chiefai.personalagent.integration.openai;

import com.chiefai.personalagent.action.application.ToolDefinition;
import com.chiefai.personalagent.agent.domain.AgentDefinition;
import com.chiefai.personalagent.expenses.application.ExpenseExtraction;

import java.util.Collection;

public interface LanguageModelGateway {
    RouteResult route(String eventText, String managerInstructions, String routingSchema, Collection<AgentDefinition> specialists, int maxRoutes);
    SpecialistResult runSpecialist(AgentDefinition agent, String instructions, String segment, String knowledgeContext, Collection<ToolDefinition> tools, String schema, String history);
    ExpenseExtraction extractExpense(String trustedText);
}
