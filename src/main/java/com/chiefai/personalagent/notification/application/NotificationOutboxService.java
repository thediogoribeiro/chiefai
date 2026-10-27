package com.chiefai.personalagent.notification.application;

import com.chiefai.personalagent.notification.domain.NotificationStatus;
import com.chiefai.personalagent.notification.infrastructure.NotificationOutboxEntity;
import com.chiefai.personalagent.notification.infrastructure.NotificationOutboxJpaRepository;
import com.chiefai.personalagent.integration.telegram.TelegramReplyGateway;
import com.chiefai.personalagent.shared.Jsonb;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationOutboxService {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxService.class);
    private static final int TELEGRAM_LIMIT = 4096;
    private final NotificationOutboxJpaRepository outbox;
    private final TelegramReplyGateway telegram;
    private final PersonalAgentProperties properties;

    public NotificationOutboxService(NotificationOutboxJpaRepository outbox, TelegramReplyGateway telegram, PersonalAgentProperties properties) {
        this.outbox = outbox;
        this.telegram = telegram;
        this.properties = properties;
    }

    @Transactional
    public void enqueue(String channel, String destination, JsonNode payload, String idempotencyKey) {
        outbox.findByIdempotencyKey(idempotencyKey).orElseGet(() -> outbox.save(NotificationOutboxEntity.pending(channel, destination, payload, idempotencyKey)));
    }

    @Scheduled(fixedDelayString = "${personal-agent.workers.notification-delay:5000}")
    public void sendPendingWorker() {
        if (properties.workers() != null && !properties.workers().enabled()) {
            return;
        }
        sendPending();
    }

    @Transactional
    public void sendPending() {
        int maxAttempts = properties.workers() == null ? 5 : properties.workers().maxAttempts();
        for (NotificationOutboxEntity notification : outbox.findProcessable(java.util.List.of(NotificationStatus.PENDING, NotificationStatus.FAILED), java.time.OffsetDateTime.now())) {
            try {
                notification.markSending();
                if ("TELEGRAM".equals(notification.getChannel())) {
                    sendTelegram(notification);
                } else {
                    log.info("Fake notification sent channel={} idempotencyKey={}", notification.getChannel(), notification.getIdempotencyKey());
                }
                notification.markSent();
            } catch (RuntimeException ex) {
                log.warn("Notification send failed channel={} idempotencyKey={} error={}", notification.getChannel(), notification.getIdempotencyKey(), ex.getMessage());
                notification.markFailed(ex.getMessage(), maxAttempts);
            }
        }
    }

    private void sendTelegram(NotificationOutboxEntity notification) {
        String text = notification.getPayload().hasNonNull("text")
                ? notification.getPayload().get("text").asText()
                : notification.getPayload().path("reply").asText("");
        for (String part : splitTelegram(text)) {
            telegram.sendText(notification.getDestination(), part);
        }
    }

    private java.util.List<String> splitTelegram(String text) {
        String value = text == null || text.isBlank() ? "Processado." : text;
        java.util.List<String> parts = new java.util.ArrayList<>();
        int cursor = 0;
        while (cursor < value.length()) {
            int end = Math.min(value.length(), cursor + TELEGRAM_LIMIT);
            parts.add(value.substring(cursor, end));
            cursor = end;
        }
        return parts;
    }

    public void enqueueTelegram(String chatId, String text, String idempotencyKey) {
        enqueue("TELEGRAM", chatId, Jsonb.value(java.util.Map.of("text", text)), idempotencyKey);
    }
}
