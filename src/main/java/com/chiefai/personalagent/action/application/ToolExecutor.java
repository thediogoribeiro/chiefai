package com.chiefai.personalagent.action.application;

import com.chiefai.personalagent.action.domain.ToolExecutionStatus;
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

    public ToolExecutor(ToolRegistry registry, ToolExecutionJpaRepository executions) {
        this.registry = registry;
        this.executions = executions;
    }

    @Transactional
    public ToolExecutionResult execute(UUID proposedActionId, UUID eventId, String agentId, String toolName, JsonNode arguments, String idempotencyKey) {
        if (executions.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return ToolExecutionResult.success(null);
        }
        ToolExecutionResult result = registry.execute(toolName, arguments, idempotencyKey);
        executions.save(new ToolExecutionEntity(proposedActionId, eventId, agentId, toolName, arguments, idempotencyKey,
                result.success() ? ToolExecutionStatus.SUCCESS : ToolExecutionStatus.FAILED, result.result(), result.errorMessage()));
        return result;
    }
}
