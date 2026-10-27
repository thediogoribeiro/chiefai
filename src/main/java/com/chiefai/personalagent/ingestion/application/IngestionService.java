package com.chiefai.personalagent.ingestion.application;

import com.chiefai.personalagent.ingestion.infrastructure.IncomingEventEntity;
import com.chiefai.personalagent.ingestion.infrastructure.IncomingEventJpaRepository;
import com.chiefai.personalagent.shared.Jsonb;
import com.chiefai.personalagent.shared.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class IngestionService {
    private final IncomingEventJpaRepository events;

    public IngestionService(IncomingEventJpaRepository events) {
        this.events = events;
    }

    @Transactional
    public EventDtos.EventResponse accept(EventDtos.CreateEventRequest request) {
        return events.findBySourceAndExternalId(request.source(), request.externalId())
                .map(existing -> toResponse(existing, true))
                .orElseGet(() -> create(request));
    }

    private EventDtos.EventResponse create(EventDtos.CreateEventRequest request) {
        try {
            IncomingEventEntity entity = IncomingEventEntity.create(request.externalId(), request.source(), request.type(), request.occurredAt(),
                    request.content(), request.metadata() == null ? Jsonb.object() : request.metadata());
            return toResponse(events.saveAndFlush(entity), false);
        } catch (DataIntegrityViolationException race) {
            IncomingEventEntity existing = events.findBySourceAndExternalId(request.source(), request.externalId()).orElseThrow();
            return toResponse(existing, true);
        }
    }

    @Transactional(readOnly = true)
    public EventDtos.EventResponse get(UUID id) {
        return events.findById(id).map(event -> toResponse(event, false)).orElseThrow(() -> new NotFoundException("Event not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<EventDtos.EventResponse> list() {
        return events.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 100)).stream().map(event -> toResponse(event, false)).toList();
    }

    private EventDtos.EventResponse toResponse(IncomingEventEntity e, boolean duplicate) {
        return new EventDtos.EventResponse(e.getId(), e.getExternalId(), e.getSource(), e.getType(), e.getStatus(), e.getOccurredAt(),
                e.getContent(), e.getMetadata(), duplicate, e.getCreatedAt(), e.getUpdatedAt());
    }
}
