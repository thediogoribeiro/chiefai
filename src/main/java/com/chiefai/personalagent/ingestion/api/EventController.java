package com.chiefai.personalagent.ingestion.api;

import com.chiefai.personalagent.ingestion.application.EventDtos;
import com.chiefai.personalagent.ingestion.application.IngestionService;
import com.chiefai.personalagent.orchestration.application.EventProcessingService;
import com.chiefai.personalagent.orchestration.infrastructure.AgentExecutionEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final IngestionService ingestion;
    private final EventProcessingService processing;

    public EventController(IngestionService ingestion, EventProcessingService processing) {
        this.ingestion = ingestion;
        this.processing = processing;
    }

    @PostMapping
    ResponseEntity<EventDtos.EventResponse> create(@Valid @RequestBody EventDtos.CreateEventRequest request) {
        EventDtos.EventResponse response = ingestion.accept(request);
        return ResponseEntity.status(response.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}")
    EventDtos.EventResponse get(@PathVariable UUID id) {
        return ingestion.get(id);
    }

    @GetMapping
    List<EventDtos.EventResponse> list() {
        return ingestion.list();
    }

    @GetMapping("/{id}/executions")
    List<ExecutionResponse> executions(@PathVariable UUID id) {
        return processing.executionsFor(id).stream().map(ExecutionResponse::from).toList();
    }

    public record ExecutionResponse(UUID id, UUID eventId, String agentId, String segment, String status, String model,
                                    Object result, String errorMessage) {
        static ExecutionResponse from(AgentExecutionEntity e) {
            return new ExecutionResponse(e.getId(), e.getEventId(), e.getAgentId(), e.getSegment(), e.getStatus().name(), e.getModel(), e.getResult(), e.getErrorMessage());
        }
    }
}
