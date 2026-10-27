package com.chiefai.personalagent.task.infrastructure;

import com.chiefai.personalagent.shared.InvalidStateException;
import com.chiefai.personalagent.task.domain.TaskPriority;
import com.chiefai.personalagent.task.domain.TaskStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "personal_task")
public class PersonalTaskEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String title;
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(name = "source_event_id")
    private UUID sourceEventId;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Version
    private long version;

    public PersonalTaskEntity() {
    }

    public static PersonalTaskEntity create(String title, String description, TaskPriority priority, LocalDate dueDate, UUID sourceEventId) {
        OffsetDateTime now = OffsetDateTime.now();
        PersonalTaskEntity entity = new PersonalTaskEntity();
        entity.id = UUID.randomUUID();
        entity.title = title;
        entity.description = description;
        entity.priority = priority == null ? TaskPriority.NORMAL : priority;
        entity.dueDate = dueDate;
        entity.sourceEventId = sourceEventId;
        entity.status = TaskStatus.OPEN;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void complete() {
        if (status == TaskStatus.CANCELLED) {
            throw new InvalidStateException("Cancelled tasks cannot be completed");
        }
        status = TaskStatus.COMPLETED;
        updatedAt = OffsetDateTime.now();
    }

    public void cancel() {
        if (status == TaskStatus.COMPLETED) {
            throw new InvalidStateException("Completed tasks cannot be cancelled");
        }
        status = TaskStatus.CANCELLED;
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public TaskPriority getPriority() { return priority; }
    public LocalDate getDueDate() { return dueDate; }
    public UUID getSourceEventId() { return sourceEventId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
