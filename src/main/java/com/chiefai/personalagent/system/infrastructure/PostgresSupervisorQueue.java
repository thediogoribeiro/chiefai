package com.chiefai.personalagent.system.infrastructure;

import com.chiefai.personalagent.action.domain.ActionStatus;
import com.chiefai.personalagent.action.infrastructure.ProposedActionJpaRepository;
import com.chiefai.personalagent.shared.NotFoundException;
import com.chiefai.personalagent.system.application.SupervisorQueue;
import com.chiefai.personalagent.system.application.SystemUpdateDto;
import com.chiefai.personalagent.system.domain.UpdateStatus;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
public class PostgresSupervisorQueue implements SupervisorQueue {
    private static final List<UpdateStatus> ACTIVE = List.of(UpdateStatus.REQUESTED, UpdateStatus.RUNNING);
    private static final List<UpdateStatus> DONE = List.of(UpdateStatus.NO_CHANGES, UpdateStatus.SUCCEEDED,
            UpdateStatus.FAILED_PRE_SWITCH, UpdateStatus.FAILED_START, UpdateStatus.ROLLBACK_FAILED);
    private final SystemUpdateRepository updates;
    private final ProposedActionJpaRepository actions;
    public PostgresSupervisorQueue(SystemUpdateRepository updates, ProposedActionJpaRepository actions) {
        this.updates = updates;
        this.actions = actions;
    }
    @Override public SystemUpdateDto enqueue(UUID approvedActionId) {
        var action = actions.findById(approvedActionId).orElseThrow(() -> new SecurityException("Approved action required"));
        if (action.getStatus() != ActionStatus.EXECUTING || action.getApprovedAt() == null
                || !"manager".equals(action.getAgentId()) || !"system.request_update".equals(action.getToolName())
                || !action.getArguments().isObject() || !action.getArguments().isEmpty())
            throw new SecurityException("Only an approved Manager update can enter the supervisor queue");
        var existing = updates.findByActionId(approvedActionId);
        if (existing.isPresent()) return SystemUpdateDto.from(existing.get());
        if (active()) return null;
        return SystemUpdateDto.from(updates.saveAndFlush(SystemUpdateEntity.requested(approvedActionId)));
    }
    @Override public boolean active() { return updates.existsByStatusIn(ACTIVE); }
    @Override public SystemUpdateDto latest() { return updates.findFirstByOrderByRequestedAtDesc().map(SystemUpdateDto::from).orElse(null); }
    @Override public SystemUpdateDto get(UUID id) { return updates.findById(id).map(SystemUpdateDto::from).orElseThrow(() -> new NotFoundException("Update not found")); }
    @Override public List<SystemUpdateDto> completedWithoutNotification() {
        return updates.findByNotifiedAtIsNullAndStatusIn(DONE).stream().map(SystemUpdateDto::from).toList();
    }
    @Override public void markNotified(UUID id) { updates.findById(id).orElseThrow().markNotified(); }
}
