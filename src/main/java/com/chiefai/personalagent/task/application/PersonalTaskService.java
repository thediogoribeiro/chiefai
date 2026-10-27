package com.chiefai.personalagent.task.application;

import com.chiefai.personalagent.shared.NotFoundException;
import com.chiefai.personalagent.task.domain.TaskPriority;
import com.chiefai.personalagent.task.domain.TaskStatus;
import com.chiefai.personalagent.task.infrastructure.PersonalTaskEntity;
import com.chiefai.personalagent.task.infrastructure.PersonalTaskJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PersonalTaskService {
    private final PersonalTaskJpaRepository tasks;

    public PersonalTaskService(PersonalTaskJpaRepository tasks) {
        this.tasks = tasks;
    }

    @Transactional
    public PersonalTaskDto create(String title, String description, TaskPriority priority, LocalDate dueDate, UUID sourceEventId) {
        return toDto(tasks.save(PersonalTaskEntity.create(title, description, priority, dueDate, sourceEventId)));
    }

    @Transactional(readOnly = true)
    public List<PersonalTaskDto> list(TaskStatus status) {
        return (status == null ? tasks.findAllByOrderByCreatedAtDesc() : tasks.findByStatusOrderByCreatedAtDesc(status))
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PersonalTaskDto get(UUID id) {
        return tasks.findById(id).map(this::toDto).orElseThrow(() -> new NotFoundException("Task not found: " + id));
    }

    @Transactional
    public PersonalTaskDto complete(UUID id) {
        PersonalTaskEntity task = tasks.findById(id).orElseThrow(() -> new NotFoundException("Task not found: " + id));
        task.complete();
        return toDto(task);
    }

    @Transactional
    public PersonalTaskDto cancel(UUID id) {
        PersonalTaskEntity task = tasks.findById(id).orElseThrow(() -> new NotFoundException("Task not found: " + id));
        task.cancel();
        return toDto(task);
    }

    private PersonalTaskDto toDto(PersonalTaskEntity e) {
        return new PersonalTaskDto(e.getId(), e.getTitle(), e.getDescription(), e.getStatus(), e.getPriority(), e.getDueDate(), e.getSourceEventId(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
