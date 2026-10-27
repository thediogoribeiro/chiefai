package com.chiefai.personalagent.ingestion.application;

import com.chiefai.personalagent.ingestion.domain.EventSource;
import com.chiefai.personalagent.ingestion.domain.EventStatus;
import com.chiefai.personalagent.ingestion.domain.EventType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class EventDtos {
    private EventDtos() {
    }

    public record CreateEventRequest(@NotBlank String externalId, @NotNull EventSource source, @NotNull EventType type,
                                     OffsetDateTime occurredAt, @NotNull JsonNode content, JsonNode metadata) {
    }

    public record EventResponse(UUID id, String externalId, EventSource source, EventType type, EventStatus status,
                                OffsetDateTime occurredAt, JsonNode content, JsonNode metadata, boolean duplicate,
                                OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }
}
