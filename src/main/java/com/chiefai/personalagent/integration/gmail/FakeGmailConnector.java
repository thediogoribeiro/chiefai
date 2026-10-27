package com.chiefai.personalagent.integration.gmail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Primary
@ConditionalOnProperty(prefix = "personal-agent.google", name = "enabled", havingValue = "false", matchIfMissing = true)
public class FakeGmailConnector implements GmailConnector {
    private final Map<String, GmailMessage> messages = new ConcurrentHashMap<>();
    private final List<String> queriedLabels = Collections.synchronizedList(new ArrayList<>());

    @Override
    public List<GmailMessage> findMessagesByLabel(String label, int limit) {
        queriedLabels.add(label);
        return messages.values().stream()
                .filter(message -> message.labels().contains(label))
                .limit(limit)
                .toList();
    }

    @Override
    public void addLabel(String messageId, String label) {
        GmailMessage current = messages.get(messageId);
        if (current == null) {
            return;
        }
        Set<String> labels = new LinkedHashSet<>(current.labels());
        labels.add(label);
        messages.put(messageId, new GmailMessage(current.messageId(), current.threadId(), current.receivedAt(), current.subject(), current.from(), current.bodyText(), labels, current.attachments()));
    }

    public void addMessage(GmailMessage message) {
        messages.put(message.messageId(), message);
    }

    public List<String> queriedLabels() {
        return List.copyOf(queriedLabels);
    }

    public Optional<GmailMessage> get(String messageId) {
        return Optional.ofNullable(messages.get(messageId));
    }
}
