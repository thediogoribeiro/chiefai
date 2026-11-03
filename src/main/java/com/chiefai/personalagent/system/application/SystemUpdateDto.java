package com.chiefai.personalagent.system.application;

import com.chiefai.personalagent.system.domain.UpdateStatus;
import com.chiefai.personalagent.system.infrastructure.SystemUpdateEntity;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SystemUpdateDto(UUID id, UUID actionId, UpdateStatus status, OffsetDateTime requestedAt,
                              OffsetDateTime startedAt, OffsetDateTime completedAt, String previousCommit,
                              String newCommit, String errorSummary) {
    public static SystemUpdateDto from(SystemUpdateEntity e) {
        return new SystemUpdateDto(e.getId(), e.getActionId(), e.getStatus(), e.getRequestedAt(), e.getStartedAt(),
                e.getCompletedAt(), e.getPreviousCommit(), e.getNewCommit(), e.getErrorSummary());
    }
}
