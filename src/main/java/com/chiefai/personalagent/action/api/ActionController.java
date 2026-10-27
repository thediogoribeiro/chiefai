package com.chiefai.personalagent.action.api;

import com.chiefai.personalagent.action.application.ActionDto;
import com.chiefai.personalagent.action.application.ActionService;
import com.chiefai.personalagent.action.domain.ActionStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/actions")
public class ActionController {
    private final ActionService service;

    public ActionController(ActionService service) {
        this.service = service;
    }

    @GetMapping
    List<ActionDto> list(@RequestParam(required = false) ActionStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    ActionDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve")
    ActionDto approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/reject")
    ActionDto reject(@PathVariable UUID id) {
        return service.reject(id);
    }
}
