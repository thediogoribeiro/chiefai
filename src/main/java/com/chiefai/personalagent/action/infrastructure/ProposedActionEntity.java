package com.chiefai.personalagent.action.infrastructure;

import com.chiefai.personalagent.action.domain.ActionStatus;
import com.chiefai.personalagent.action.domain.RiskLevel;
import com.chiefai.personalagent.shared.InvalidStateException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "proposed_action")
public class ProposedActionEntity {
    @Id
    private UUID id;
    @Column(name = "event_id")
    private UUID eventId;
    @Column(name = "agent_execution_id")
    private UUID agentExecutionId;
    @Column(name = "agent_id", nullable = false)
    private String agentId;
    @Column(name = "tool_name", nullable = false)
    private String toolName;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode arguments;
    @Column(nullable = false)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionStatus status;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode result;
    @Column(name = "error_message")
    private String errorMessage;
    private int attempts;
    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;
    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;
    @Column(name = "executed_at")
    private OffsetDateTime executedAt;
    @Version
    private long version;

    public ProposedActionEntity() {
    }

    public static ProposedActionEntity create(UUID eventId, UUID executionId, String agentId, String toolName, JsonNode arguments, String reason, RiskLevel riskLevel, String idempotencyKey, boolean needsApproval) {
        OffsetDateTime now = OffsetDateTime.now();
        ProposedActionEntity entity = new ProposedActionEntity();
        entity.id = UUID.randomUUID();
        entity.eventId = eventId;
        entity.agentExecutionId = executionId;
        entity.agentId = agentId;
        entity.toolName = toolName;
        entity.arguments = arguments;
        entity.reason = reason;
        entity.riskLevel = riskLevel;
        entity.status = needsApproval ? ActionStatus.PENDING_APPROVAL : ActionStatus.APPROVED;
        entity.idempotencyKey = idempotencyKey;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void approve() {
        if (status == ActionStatus.APPROVED || status == ActionStatus.EXECUTING || status == ActionStatus.EXECUTED) {
            return;
        }
        if (status != ActionStatus.PENDING_APPROVAL) {
            throw new InvalidStateException("Only pending actions can be approved");
        }
        status = ActionStatus.APPROVED;
        approvedAt = OffsetDateTime.now();
        updatedAt = approvedAt;
    }

    public void reject() {
        if (status != ActionStatus.PENDING_APPROVAL) {
            throw new InvalidStateException("Only pending actions can be rejected");
        }
        status = ActionStatus.REJECTED;
        rejectedAt = OffsetDateTime.now();
        updatedAt = rejectedAt;
    }

    public void markExecuting() {
        if (status != ActionStatus.APPROVED) {
            throw new InvalidStateException("Only approved actions can execute");
        }
        status = ActionStatus.EXECUTING;
        updatedAt = OffsetDateTime.now();
    }

    public void markExecuted(JsonNode result) {
        status = ActionStatus.EXECUTED;
        this.result = result;
        executedAt = OffsetDateTime.now();
        updatedAt = executedAt;
    }

    public void markFailed(String message, int maxAttempts) {
        attempts++;
        errorMessage = message;
        updatedAt = OffsetDateTime.now();
        if (attempts >= maxAttempts) {
            status = ActionStatus.FAILED;
        } else {
            status = ActionStatus.APPROVED;
            nextAttemptAt = updatedAt.plusSeconds(Math.min(300, 10L * attempts * attempts));
        }
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getAgentExecutionId() { return agentExecutionId; }
    public String getAgentId() { return agentId; }
    public String getToolName() { return toolName; }
    public JsonNode getArguments() { return arguments; }
    public String getReason() { return reason; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public ActionStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public JsonNode getResult() { return result; }
    public String getErrorMessage() { return errorMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
}
