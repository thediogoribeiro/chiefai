package com.chiefai.personalagent.action.application;

import com.chiefai.personalagent.action.domain.ActionStatus;
import com.chiefai.personalagent.access.OwnerAuthorization;
import com.chiefai.personalagent.ingestion.infrastructure.IncomingEventJpaRepository;
import com.chiefai.personalagent.system.domain.ManagerSystemCommand;
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
    private final IncomingEventJpaRepository events;

    public ActionService(ProposedActionJpaRepository actions, ToolRegistry registry, ToolPolicyService policy, ToolExecutor executor, PersonalAgentProperties properties, TransactionTemplate transactionTemplate, IncomingEventJpaRepository events) {
        this.actions = actions;
        this.registry = registry;
        this.policy = policy;
        this.executor = executor;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.events = events;
    }

    @Transactional
    public ActionDto proposeOrAutoExecute(UUID eventId, UUID executionId, String agentId, String toolName, JsonNode arguments, String reason, boolean modelRequiresApproval) {
        policy.assertAllowed(agentId, toolName);
        if ("system.request_update".equals(toolName)) {
            var event = eventId == null ? null : events.findById(eventId).orElse(null);
            if (event == null || !event.isAuthenticatedOwnerText()
                    || ManagerSystemCommand.parse(event.getContent().path("text").asText()).orElse(null) != ManagerSystemCommand.UPDATE
                    || arguments == null || !arguments.isObject() || !arguments.isEmpty())
                throw new SecurityException("An explicit authenticated owner request without arguments is required for updates");
        }
        ToolDefinition definition = registry.definition(toolName);
        boolean needsApproval = modelRequiresApproval || policy.requiresApproval(definition);
        String key = eventId + ":" + executionId + ":" + agentId + ":" + toolName + ":" + Integer.toHexString((arguments == null ? "" : arguments.toString()).hashCode());
        ProposedActionEntity entity = actions.findByIdempotencyKey(key).orElseGet(() ->
                actions.save(ProposedActionEntity.create(eventId, executionId, agentId, toolName, arguments == null ? Jsonb.object() : arguments, reason, definition.riskLevel(), key, needsApproval)));
        return toDto(entity);
    }

    /** Called only by the authenticated owner entry points, never by model output. */
    @Transactional
    public ActionDto proposeSystemUpdate() {
        return proposeSystemUpdate(UUID.randomUUID());
    }

    @Transactional
    public ActionDto proposeSystemUpdate(UUID requestId) {
        OwnerAuthorization.requireHumanOwner();
        policy.assertAllowed("manager", "system.request_update");
        String key = "owner-system-update:" + requestId;
        var existing = actions.findByIdempotencyKey(key);
        if (existing.isPresent()) return toDto(existing.get());
        ProposedActionEntity entity = ProposedActionEntity.create(null, null, "manager", "system.request_update",
                Jsonb.object(), "Atualização solicitada explicitamente pelo proprietário", registry.definition("system.request_update").riskLevel(),
                key, true);
        return toDto(actions.save(entity));
    }

    @Transactional
    public ToolExecutionResult executeReadOnly(UUID eventId, UUID executionId, String agentId, String toolName) {
        policy.assertAllowed(agentId, toolName);
        if (registry.definition(toolName).riskLevel() != com.chiefai.personalagent.action.domain.RiskLevel.READ_ONLY)
            throw new SecurityException("Only read operations may execute without approval");
        return executor.execute(null, eventId, agentId, toolName, Jsonb.object(), "read:" + executionId + ":" + toolName);
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
        if ("system.request_update".equals(action.getToolName())) OwnerAuthorization.requireHumanOwner();
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
