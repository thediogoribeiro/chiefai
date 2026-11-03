package com.chiefai.personalagent.action.application;

import com.chiefai.personalagent.action.domain.ToolExecutionStatus;
import com.chiefai.personalagent.action.domain.ActionStatus;
import com.chiefai.personalagent.action.infrastructure.ProposedActionJpaRepository;
import com.chiefai.personalagent.action.infrastructure.ToolExecutionEntity;
import com.chiefai.personalagent.action.infrastructure.ToolExecutionJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ToolExecutor {
    private final ToolRegistry registry;
    private final ToolExecutionJpaRepository executions;
    private final ToolPolicyService policy;
    private final ProposedActionJpaRepository actions;

    public ToolExecutor(ToolRegistry registry, ToolExecutionJpaRepository executions, ToolPolicyService policy, ProposedActionJpaRepository actions) {
        this.registry = registry;
        this.executions = executions;
        this.policy = policy;
        this.actions = actions;
    }

    @Transactional
    public ToolExecutionResult execute(UUID proposedActionId, UUID eventId, String agentId, String toolName, JsonNode arguments, String idempotencyKey) {
        policy.assertAllowed(agentId, toolName);
        if ("system.request_update".equals(toolName)) {
            var action = proposedActionId == null ? null : actions.findById(proposedActionId).orElse(null);
            if (action == null || action.getStatus() != ActionStatus.EXECUTING || action.getApprovedAt() == null
                    || !"manager".equals(action.getAgentId()) || !toolName.equals(action.getToolName())
                    || !java.util.Objects.equals(action.getEventId(), eventId)
                    || arguments == null || !arguments.isObject() || !arguments.isEmpty() || !arguments.equals(action.getArguments())
                    || !("action:" + proposedActionId).equals(idempotencyKey))
                throw new SecurityException("Update requires an explicitly approved manager action without arguments");
        }
        var existing = executions.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            var prior = existing.get();
            return prior.getStatus() == ToolExecutionStatus.SUCCESS
                    ? ToolExecutionResult.success(prior.getResult()) : ToolExecutionResult.failure(prior.getErrorMessage());
        }
        ToolExecutionResult result = registry.execute(toolName, arguments, idempotencyKey);
        executions.save(new ToolExecutionEntity(proposedActionId, eventId, agentId, toolName, arguments, idempotencyKey,
                result.success() ? ToolExecutionStatus.SUCCESS : ToolExecutionStatus.FAILED, result.result(), result.errorMessage()));
        return result;
    }
}
