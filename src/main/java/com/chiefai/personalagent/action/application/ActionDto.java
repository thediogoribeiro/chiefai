package com.chiefai.personalagent.action.application;

import com.chiefai.personalagent.action.domain.ActionStatus;
import com.chiefai.personalagent.action.domain.RiskLevel;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ActionDto(UUID id, UUID eventId, UUID agentExecutionId, String agentId, String toolName, JsonNode arguments,
                        String reason, RiskLevel riskLevel, ActionStatus status, JsonNode result, String errorMessage,
                        OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
