package com.chiefai.personalagent.system.application;

import java.util.List;
import java.util.UUID;

/** Durable protocol shared with the external supervisor; contains no shell or repository arguments. */
public interface SupervisorQueue {
    SystemUpdateDto enqueue(UUID approvedActionId);
    boolean active();
    SystemUpdateDto latest();
    SystemUpdateDto get(UUID id);
    List<SystemUpdateDto> completedWithoutNotification();
    void markNotified(UUID id);
}
