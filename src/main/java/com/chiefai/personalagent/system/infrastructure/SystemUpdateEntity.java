package com.chiefai.personalagent.system.infrastructure;

import com.chiefai.personalagent.system.domain.UpdateStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "system_update")
public class SystemUpdateEntity {
    @Id private UUID id;
    @Column(name = "action_id", nullable = false, unique = true) private UUID actionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UpdateStatus status;
    @Column(name = "requested_at", nullable = false) private OffsetDateTime requestedAt;
    @Column(name = "started_at") private OffsetDateTime startedAt;
    @Column(name = "completed_at") private OffsetDateTime completedAt;
    @Column(name = "previous_commit") private String previousCommit;
    @Column(name = "new_commit") private String newCommit;
    @Column(name = "error_summary") private String errorSummary;
    @Column(name = "notified_at") private OffsetDateTime notifiedAt;

    protected SystemUpdateEntity() { }
    public static SystemUpdateEntity requested(UUID actionId) {
        SystemUpdateEntity e = new SystemUpdateEntity();
        e.id = UUID.randomUUID(); e.actionId = actionId; e.status = UpdateStatus.REQUESTED;
        e.requestedAt = OffsetDateTime.now();
        return e;
    }
    public void markNotified() { notifiedAt = OffsetDateTime.now(); }
    public UUID getId() { return id; }
    public UUID getActionId() { return actionId; }
    public UpdateStatus getStatus() { return status; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public String getPreviousCommit() { return previousCommit; }
    public String getNewCommit() { return newCommit; }
    public String getErrorSummary() { return errorSummary; }
}
