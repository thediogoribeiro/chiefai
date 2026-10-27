package com.chiefai.personalagent.action.application;

import com.chiefai.personalagent.action.domain.ActionStatus;
import com.chiefai.personalagent.action.infrastructure.ProposedActionEntity;
import com.chiefai.personalagent.action.infrastructure.ProposedActionJpaRepository;
import com.chiefai.personalagent.shared.Jsonb;
import com.chiefai.personalagent.shared.NotFoundException;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ActionService {
    private final ProposedActionJpaRepository actions;
    private final ToolRegistry registry;
    private final ToolPolicyService policy;
    private final ToolExecutor executor;
    private final PersonalAgentProperties properties;
    private final TransactionTemplate transactionTemplate;

    public ActionService(ProposedActionJpaRepository actions, ToolRegistry registry, ToolPolicyService policy, ToolExecutor executor, PersonalAgentProperties properties, TransactionTemplate transactionTemplate) {
        this.actions = actions;
        this.registry = registry;
        this.policy = policy;
        this.executor = executor;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public ActionDto proposeOrAutoExecute(UUID eventId, UUID executionId, String agentId, String toolName, JsonNode arguments, String reason, boolean modelRequiresApproval) {
        ToolDefinition definition = registry.definition(toolName);
        boolean needsApproval = modelRequiresApproval || policy.requiresApproval(definition);
        String key = eventId + ":" + executionId + ":" + agentId + ":" + toolName + ":" + Integer.toHexString((arguments == null ? "" : arguments.toString()).hashCode());
        ProposedActionEntity entity = actions.findByIdempotencyKey(key).orElseGet(() ->
                actions.save(ProposedActionEntity.create(eventId, executionId, agentId, toolName, arguments == null ? Jsonb.object() : arguments, reason, definition.riskLevel(), key, needsApproval)));
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<ActionDto> list(ActionStatus status) {
        return actions.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 100)).stream()
                .filter(action -> status == null || action.getStatus() == status)
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ActionDto get(UUID id) {
        return actions.findById(id).map(this::toDto).orElseThrow(() -> new NotFoundException("Action not found: " + id));
    }

    @Transactional
    public ActionDto approve(UUID id) {
        ProposedActionEntity action = actions.findById(id).orElseThrow(() -> new NotFoundException("Action not found: " + id));
        action.approve();
        return toDto(action);
    }

    @Transactional
    public ActionDto reject(UUID id) {
        ProposedActionEntity action = actions.findById(id).orElseThrow(() -> new NotFoundException("Action not found: " + id));
        action.reject();
        return toDto(action);
    }

    @Scheduled(fixedDelayString = "${personal-agent.workers.action-delay:3000}")
    public void executeApprovedWorker() {
        if (properties.workers() != null && !properties.workers().enabled()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> executeApprovedOnce());
    }

    @Transactional
    public int executeApprovedOnce() {
        int batch = properties.workers() == null ? 10 : properties.workers().batchSize();
        int maxAttempts = properties.workers() == null ? 5 : properties.workers().maxAttempts();
        List<ProposedActionEntity> locked = actions.lockApproved(ActionStatus.APPROVED, OffsetDateTime.now(), PageRequest.of(0, batch));
        for (ProposedActionEntity action : locked) {
            action.markExecuting();
            ToolExecutionResult result = executor.execute(action.getId(), action.getEventId(), action.getAgentId(), action.getToolName(), action.getArguments(), "action:" + action.getId());
            if (result.success()) {
                action.markExecuted(result.result());
            } else {
                action.markFailed(result.errorMessage(), maxAttempts);
            }
        }
        return locked.size();
    }

    private ActionDto toDto(ProposedActionEntity e) {
        return new ActionDto(e.getId(), e.getEventId(), e.getAgentExecutionId(), e.getAgentId(), e.getToolName(), e.getArguments(), e.getReason(),
                e.getRiskLevel(), e.getStatus(), e.getResult(), e.getErrorMessage(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
