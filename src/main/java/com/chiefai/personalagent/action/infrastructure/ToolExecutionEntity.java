package com.chiefai.personalagent.action.infrastructure;

import com.chiefai.personalagent.action.domain.ToolExecutionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tool_execution")
public class ToolExecutionEntity {
    @Id
    private UUID id;
    @Column(name = "proposed_action_id")
    private UUID proposedActionId;
    @Column(name = "event_id")
    private UUID eventId;
    @Column(name = "agent_id", nullable = false)
    private String agentId;
    @Column(name = "tool_name", nullable = false)
    private String toolName;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode arguments;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ToolExecutionStatus status;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode result;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public ToolExecutionEntity() {
    }

    public ToolExecutionEntity(UUID proposedActionId, UUID eventId, String agentId, String toolName, JsonNode arguments, String idempotencyKey, ToolExecutionStatus status, JsonNode result, String errorMessage) {
        this.id = UUID.randomUUID();
        this.proposedActionId = proposedActionId;
        this.eventId = eventId;
        this.agentId = agentId;
        this.toolName = toolName;
        this.arguments = arguments;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.result = result;
        this.errorMessage = errorMessage;
        this.createdAt = OffsetDateTime.now();
        this.completedAt = this.createdAt;
    }
    public ToolExecutionStatus getStatus() { return status; }
    public JsonNode getResult() { return result; }
    public String getErrorMessage() { return errorMessage; }
}
