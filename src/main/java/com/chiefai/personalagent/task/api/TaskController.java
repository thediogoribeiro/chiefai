package com.chiefai.personalagent.task.api;

import com.chiefai.personalagent.task.application.PersonalTaskDto;
import com.chiefai.personalagent.task.application.PersonalTaskService;
import com.chiefai.personalagent.task.domain.TaskPriority;
import com.chiefai.personalagent.task.domain.TaskStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final PersonalTaskService service;

    public TaskController(PersonalTaskService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PersonalTaskDto create(@Valid @RequestBody CreateTaskRequest request) {
        return service.create(request.title(), request.description(), request.priority() == null ? TaskPriority.NORMAL : request.priority(), request.dueDate(), null);
    }

    @GetMapping
    List<PersonalTaskDto> list(@RequestParam(required = false) TaskStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    PersonalTaskDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/complete")
    PersonalTaskDto complete(@PathVariable UUID id) {
        return service.complete(id);
    }

    @PostMapping("/{id}/cancel")
    PersonalTaskDto cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    public record CreateTaskRequest(@NotBlank String title, String description, TaskPriority priority, LocalDate dueDate) {
    }
}
