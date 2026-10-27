package com.chiefai.personalagent.notification.infrastructure;

import com.chiefai.personalagent.notification.domain.NotificationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_outbox")
public class NotificationOutboxEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String channel;
    private String destination;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;
    private int attempts;
    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(name = "sent_at")
    private OffsetDateTime sentAt;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Version
    private long version;

    public NotificationOutboxEntity() {
    }

    public static NotificationOutboxEntity pending(String channel, String destination, JsonNode payload, String idempotencyKey) {
        OffsetDateTime now = OffsetDateTime.now();
        NotificationOutboxEntity entity = new NotificationOutboxEntity();
        entity.id = UUID.randomUUID();
        entity.channel = channel;
        entity.destination = destination;
        entity.payload = payload;
        entity.status = NotificationStatus.PENDING;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.idempotencyKey = idempotencyKey;
        return entity;
    }

    public void markSent() {
        status = NotificationStatus.SENT;
        sentAt = OffsetDateTime.now();
        updatedAt = sentAt;
    }

    public void markSending() {
        status = NotificationStatus.SENDING;
        updatedAt = OffsetDateTime.now();
    }

    public void markFailed(String error, int maxAttempts) {
        attempts++;
        errorMessage = error == null ? "Notification failed" : error;
        updatedAt = OffsetDateTime.now();
        if (attempts >= maxAttempts) {
            status = NotificationStatus.DEAD_LETTER;
        } else {
            status = NotificationStatus.FAILED;
            nextAttemptAt = updatedAt.plusSeconds(Math.min(300, 5L * attempts * attempts));
        }
    }

    public UUID getId() { return id; }
    public String getChannel() { return channel; }
    public String getDestination() { return destination; }
    public JsonNode getPayload() { return payload; }
    public NotificationStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
