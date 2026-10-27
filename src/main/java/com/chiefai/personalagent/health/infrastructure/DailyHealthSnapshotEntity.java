package com.chiefai.personalagent.health.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "health_daily_snapshot", uniqueConstraints = @UniqueConstraint(name = "uk_health_snapshot_date_source", columnNames = {"snapshot_date", "source"}))
public class DailyHealthSnapshotEntity {
    @Id
    private UUID id;
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate date;
    @Column(nullable = false)
    private String source;
    @Column(name = "sleep_score")
    private Integer sleepScore;
    @Column(name = "readiness_score")
    private Integer readinessScore;
    @Column(name = "activity_score")
    private Integer activityScore;
    @Column(name = "resting_heart_rate")
    private BigDecimal restingHeartRate;
    @Column(name = "average_hrv")
    private BigDecimal averageHrv;
    @Column(name = "sleep_duration_minutes")
    private Integer sleepDurationMinutes;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode additionalPayload;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Version
    private long version;

    public DailyHealthSnapshotEntity() {
    }

    public DailyHealthSnapshotEntity(LocalDate date, String source) {
        OffsetDateTime now = OffsetDateTime.now();
        this.id = UUID.randomUUID();
        this.date = date;
        this.source = source;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(Integer sleepScore, Integer readinessScore, Integer activityScore, BigDecimal restingHeartRate, BigDecimal averageHrv, Integer sleepDurationMinutes, JsonNode additionalPayload) {
        this.sleepScore = sleepScore;
        this.readinessScore = readinessScore;
        this.activityScore = activityScore;
        this.restingHeartRate = restingHeartRate;
        this.averageHrv = averageHrv;
        this.sleepDurationMinutes = sleepDurationMinutes;
        this.additionalPayload = additionalPayload;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public LocalDate getDate() { return date; }
    public String getSource() { return source; }
    public Integer getSleepScore() { return sleepScore; }
    public Integer getReadinessScore() { return readinessScore; }
    public Integer getActivityScore() { return activityScore; }
    public BigDecimal getRestingHeartRate() { return restingHeartRate; }
    public BigDecimal getAverageHrv() { return averageHrv; }
    public Integer getSleepDurationMinutes() { return sleepDurationMinutes; }
    public JsonNode getAdditionalPayload() { return additionalPayload; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
