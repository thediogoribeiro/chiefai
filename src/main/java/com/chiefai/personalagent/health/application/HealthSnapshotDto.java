package com.chiefai.personalagent.health.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record HealthSnapshotDto(UUID id, LocalDate date, String source, Integer sleepScore, Integer readinessScore,
                                Integer activityScore, BigDecimal restingHeartRate, BigDecimal averageHrv,
                                Integer sleepDurationMinutes, JsonNode additionalPayload,
                                OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
