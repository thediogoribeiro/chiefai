package com.chiefai.personalagent.health.api;

import com.chiefai.personalagent.health.application.HealthSnapshotDto;
import com.chiefai.personalagent.health.application.HealthSnapshotService;
import com.chiefai.personalagent.health.application.HealthTrendDto;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final HealthSnapshotService service;

    public HealthController(HealthSnapshotService service) {
        this.service = service;
    }

    @PutMapping("/snapshots/{date}")
    HealthSnapshotDto upsert(@PathVariable LocalDate date, @RequestParam(defaultValue = "MANUAL") String source, @RequestBody JsonNode payload) {
        return service.upsert(date, source, payload);
    }

    @GetMapping("/snapshots/{date}")
    HealthSnapshotDto get(@PathVariable LocalDate date, @RequestParam(defaultValue = "MANUAL") String source) {
        return service.get(date, source);
    }

    @GetMapping("/snapshots")
    List<HealthSnapshotDto> list(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return service.list(from, to);
    }

    @GetMapping("/trends")
    HealthTrendDto trends(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return service.trends(from, to);
    }
}
