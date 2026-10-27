package com.chiefai.personalagent.orchestration.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "routing_decision")
public class RoutingDecisionEntity {
    @Id
    private UUID id;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(nullable = false)
    private String strategy;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode routes;
    @Column(name = "clarification_required", nullable = false)
    private boolean clarificationRequired;
    @Column(name = "clarification_question")
    private String clarificationQuestion;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public RoutingDecisionEntity() {
    }

    public RoutingDecisionEntity(UUID eventId, String strategy, JsonNode routes, boolean clarificationRequired, String clarificationQuestion) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.strategy = strategy;
        this.routes = routes;
        this.clarificationRequired = clarificationRequired;
        this.clarificationQuestion = clarificationQuestion;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getStrategy() { return strategy; }
    public JsonNode getRoutes() { return routes; }
    public boolean isClarificationRequired() { return clarificationRequired; }
    public String getClarificationQuestion() { return clarificationQuestion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
