package com.chiefai.personalagent.orchestration.application;

import com.chiefai.personalagent.agent.application.AgentRunner;
import com.chiefai.personalagent.ingestion.domain.EventStatus;
import com.chiefai.personalagent.ingestion.infrastructure.IncomingEventEntity;
import com.chiefai.personalagent.ingestion.infrastructure.IncomingEventJpaRepository;
import com.chiefai.personalagent.integration.openai.SpecialistResult;
import com.chiefai.personalagent.notification.application.NotificationOutboxService;
import com.chiefai.personalagent.orchestration.infrastructure.AgentExecutionEntity;
import com.chiefai.personalagent.orchestration.infrastructure.AgentExecutionJpaRepository;
import com.chiefai.personalagent.orchestration.infrastructure.RoutingDecisionEntity;
import com.chiefai.personalagent.orchestration.infrastructure.RoutingDecisionJpaRepository;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class EventProcessingService {
    private static final Logger log = LoggerFactory.getLogger(EventProcessingService.class);
    private final IncomingEventJpaRepository events;
    private final RoutingService routing;
    private final RoutingDecisionJpaRepository routingDecisions;
    private final AgentExecutionJpaRepository executions;
    private final AgentRunner runner;
    private final NotificationOutboxService outbox;
    private final PersonalAgentProperties properties;
    private final TransactionTemplate transactionTemplate;

    public EventProcessingService(IncomingEventJpaRepository events, RoutingService routing, RoutingDecisionJpaRepository routingDecisions,
                                  AgentExecutionJpaRepository executions, AgentRunner runner, NotificationOutboxService outbox,
                                  PersonalAgentProperties properties, TransactionTemplate transactionTemplate) {
        this.events = events;
        this.routing = routing;
        this.routingDecisions = routingDecisions;
        this.executions = executions;
        this.runner = runner;
        this.outbox = outbox;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${personal-agent.workers.event-delay:3000}")
    public void processWorker() {
        if (properties.workers() != null && !properties.workers().enabled()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> processOnce());
    }

    @Transactional
    public int processOnce() {
        int batch = properties.workers() == null ? 10 : properties.workers().batchSize();
        int maxAttempts = properties.workers() == null ? 5 : properties.workers().maxAttempts();
        List<IncomingEventEntity> locked = events.lockProcessable(EventStatus.RECEIVED, EventStatus.FAILED, OffsetDateTime.now(), PageRequest.of(0, batch));
        for (IncomingEventEntity event : locked) {
            try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", event.getCorrelationId())) {
                if (executions.countByEventId(event.getId()) > 0) {
                    event.markProcessed();
                    continue;
                }
                event.markProcessing();
                log.info("Processing event id={} source={}", event.getId(), event.getSource());
                RoutingService.Decision decision = routing.route(event);
                routingDecisions.save(new RoutingDecisionEntity(event.getId(), decision.strategy(), decision.routesJson(),
                        decision.result().clarificationRequired(), decision.result().clarificationQuestion()));
                List<ReplyPart> replies = new java.util.ArrayList<>();
                for (var route : decision.result().routes()) {
                    SpecialistResult result = runner.run(event, route);
                    replies.add(new ReplyPart(route.agentId(), result.reply(), result.actions() == null ? List.of() : result.actions()));
                }
                enqueueTelegramReplyIfNeeded(event, replies);
                event.markProcessed();
            } catch (RuntimeException ex) {
                log.warn("Event processing failed id={} error={}", event.getId(), ex.getMessage());
                event.markFailed(ex.getMessage(), maxAttempts);
            }
        }
        return locked.size();
    }

    @Transactional(readOnly = true)
    public List<AgentExecutionEntity> executionsFor(UUID eventId) {
        return executions.findByEventId(eventId);
    }

    private void enqueueTelegramReplyIfNeeded(IncomingEventEntity event, List<ReplyPart> replies) {
        if (event.getMetadata() == null || !"TELEGRAM".equals(event.getMetadata().path("replyChannel").asText())) {
            return;
        }
        String chatId = event.getMetadata().path("chatId").asText("");
        if (chatId.isBlank()) {
            return;
        }
        String text = composeTelegramReply(replies);
        outbox.enqueueTelegram(chatId, text, "telegram-reply:" + event.getId());
    }

    private String composeTelegramReply(List<ReplyPart> replies) {
        if (replies.isEmpty()) {
            return "Recebido. Não encontrei uma resposta útil para devolver.";
        }
        StringBuilder builder = new StringBuilder();
        for (ReplyPart part : replies) {
            String label = switch (part.agentId()) {
                case "health" -> "Saúde";
                case "rucodel" -> "Rucodel";
                case "personal" -> "Personal";
                default -> part.agentId();
            };
            if (replies.size() > 1) {
                builder.append(label).append(":\n");
            }
            builder.append(part.reply() == null || part.reply().isBlank() ? "Processado." : part.reply().trim()).append("\n");
            boolean pending = part.actions().stream().anyMatch(SpecialistResult.ActionCall::requiresApproval);
            if (pending) {
                builder.append("Ação externa proposta e pendente de aprovação.\n");
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    private record ReplyPart(String agentId, String reply, List<SpecialistResult.ActionCall> actions) {
    }
}
