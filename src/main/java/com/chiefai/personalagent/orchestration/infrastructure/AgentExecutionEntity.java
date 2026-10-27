package com.chiefai.personalagent.orchestration.infrastructure;

import com.chiefai.personalagent.orchestration.domain.ExecutionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_execution")
public class AgentExecutionEntity {
    @Id
    private UUID id;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "agent_id", nullable = false)
    private String agentId;
    @Column(nullable = false)
    private String segment;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;
    private String model;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode result;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "usage_metadata", columnDefinition = "jsonb")
    private JsonNode usageMetadata;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public AgentExecutionEntity() {
    }

    public static AgentExecutionEntity running(UUID eventId, String agentId, String segment, String model) {
        AgentExecutionEntity entity = new AgentExecutionEntity();
        entity.id = UUID.randomUUID();
        entity.eventId = eventId;
        entity.agentId = agentId;
        entity.segment = segment;
        entity.model = model;
        entity.status = ExecutionStatus.RUNNING;
        entity.startedAt = OffsetDateTime.now();
        return entity;
    }

    public void complete(JsonNode result, JsonNode usageMetadata) {
        this.status = ExecutionStatus.COMPLETED;
        this.result = result;
        this.usageMetadata = usageMetadata;
        this.completedAt = OffsetDateTime.now();
    }

    public void fail(String message) {
        this.status = ExecutionStatus.FAILED;
        this.errorMessage = message;
        this.completedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getAgentId() { return agentId; }
    public String getSegment() { return segment; }
    public ExecutionStatus getStatus() { return status; }
    public String getModel() { return model; }
    public JsonNode getResult() { return result; }
    public String getErrorMessage() { return errorMessage; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
}
