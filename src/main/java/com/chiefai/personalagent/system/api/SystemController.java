package com.chiefai.personalagent.system.api;

import com.chiefai.personalagent.action.application.ActionDto;
import com.chiefai.personalagent.access.OwnerAuthorization;
import com.chiefai.personalagent.action.application.ActionService;
import com.chiefai.personalagent.system.application.SystemRuntimeService;
import com.chiefai.personalagent.system.application.SystemUpdateDto;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    private final SystemRuntimeService system;
    private final ActionService actions;
    public SystemController(SystemRuntimeService system, ActionService actions) { this.system = system; this.actions = actions; }
    @GetMapping("/status") Map<String, Object> status() { return system.status(); }
    @GetMapping("/version") Map<String, String> version() { return system.version(); }
    @GetMapping("/updates/latest") SystemUpdateDto latest() { return system.latest(); }
    @GetMapping("/updates/{id}") SystemUpdateDto get(@PathVariable UUID id) { return system.get(id); }
    @PostMapping("/update-proposals") @ResponseStatus(HttpStatus.ACCEPTED)
    ActionDto propose(@RequestBody(required = false) JsonNode input, HttpServletRequest request) {
        OwnerAuthorization.requireHumanOwner();
        if (!request.getParameterMap().isEmpty() || (input != null && (!input.isObject() || !input.isEmpty())))
            throw new IllegalArgumentException("Update requests accept no arguments");
        return actions.proposeSystemUpdate();
    }
}
