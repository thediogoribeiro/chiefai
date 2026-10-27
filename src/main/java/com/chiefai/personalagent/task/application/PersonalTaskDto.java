package com.chiefai.personalagent.task.application;

import com.chiefai.personalagent.task.domain.TaskPriority;
import com.chiefai.personalagent.task.domain.TaskStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PersonalTaskDto(UUID id, String title, String description, TaskStatus status, TaskPriority priority,
                              LocalDate dueDate, UUID sourceEventId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
