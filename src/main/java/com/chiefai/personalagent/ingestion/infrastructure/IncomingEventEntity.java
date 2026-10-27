package com.chiefai.personalagent.ingestion.infrastructure;

import com.chiefai.personalagent.ingestion.domain.EventSource;
import com.chiefai.personalagent.ingestion.domain.EventStatus;
import com.chiefai.personalagent.ingestion.domain.EventType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "incoming_event", uniqueConstraints = @UniqueConstraint(name = "uk_incoming_event_source_external", columnNames = {"source", "external_id"}))
public class IncomingEventEntity {
    @Id
    private UUID id;
    @Column(name = "external_id", nullable = false)
    private String externalId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventSource source;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode content;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode metadata;
    private int attempts;
    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;
    @Column(name = "processing_started_at")
    private OffsetDateTime processingStartedAt;
    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
    @Column(name = "failure_reason")
    private String failureReason;
    @Column(name = "correlation_id", nullable = false)
    private String correlationId;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Version
    private long version;

    public IncomingEventEntity() {
    }

    public static IncomingEventEntity create(String externalId, EventSource source, EventType type, OffsetDateTime occurredAt, JsonNode content, JsonNode metadata) {
        OffsetDateTime now = OffsetDateTime.now();
        IncomingEventEntity entity = new IncomingEventEntity();
        entity.id = UUID.randomUUID();
        entity.externalId = externalId;
        entity.source = source;
        entity.type = type;
        entity.status = EventStatus.RECEIVED;
        entity.occurredAt = occurredAt == null ? now : occurredAt;
        entity.content = content;
        entity.metadata = metadata;
        entity.correlationId = entity.id.toString();
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void markProcessing() {
        status = EventStatus.PROCESSING;
        processingStartedAt = OffsetDateTime.now();
        updatedAt = processingStartedAt;
    }

    public void markProcessed() {
        status = EventStatus.PROCESSED;
        processedAt = OffsetDateTime.now();
        updatedAt = processedAt;
    }

    public void markFailed(String reason, int maxAttempts) {
        attempts++;
        failureReason = reason;
        updatedAt = OffsetDateTime.now();
        if (attempts >= maxAttempts) {
            status = EventStatus.DEAD_LETTER;
        } else {
            status = EventStatus.FAILED;
            nextAttemptAt = updatedAt.plusSeconds(Math.min(300, 10L * attempts * attempts));
        }
    }

    public UUID getId() { return id; }
    public String getExternalId() { return externalId; }
    public EventSource getSource() { return source; }
    public EventType getType() { return type; }
    public EventStatus getStatus() { return status; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public JsonNode getContent() { return content; }
    public JsonNode getMetadata() { return metadata; }
    public int getAttempts() { return attempts; }
    public String getFailureReason() { return failureReason; }
    public String getCorrelationId() { return correlationId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
