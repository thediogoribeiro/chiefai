package com.chiefai.personalagent.system.application;

import com.chiefai.personalagent.access.OwnerAccessConfigurationLoader;
import com.chiefai.personalagent.notification.application.NotificationOutboxService;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Service
public class SystemRuntimeService {
    private final SupervisorQueue queue;
    private final NotificationOutboxService outbox;
    private final OwnerAccessConfigurationLoader owner;
    private final PersonalAgentProperties properties;
    private final boolean enabled;
    private final String versionFile;

    public SystemRuntimeService(SupervisorQueue queue, NotificationOutboxService outbox,
                                OwnerAccessConfigurationLoader owner, PersonalAgentProperties properties,
                                @Value("${personal-agent.system.updates-enabled:false}") boolean enabled,
                                @Value("${personal-agent.system.version-file:}") String versionFile) {
        this.queue = queue; this.outbox = outbox; this.owner = owner; this.properties = properties;
        this.enabled = enabled; this.versionFile = versionFile;
    }

    @Transactional
    public SystemUpdateDto enqueueApproved(UUID actionId) {
        if (!enabled) return null;
        return queue.enqueue(actionId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        return Map.of("application", "running", "updatesEnabled", enabled,
                "updateActive", queue.active());
    }
    public Map<String, String> version() {
        String value = "unknown";
        try {
            if (versionFile != null && !versionFile.isBlank()) {
                String candidate = Files.readString(Path.of(versionFile)).trim();
                if (candidate.matches("[0-9a-f]{40,64}")) value = candidate;
            }
        } catch (Exception ignored) { /* Version is unknown until installed by the supervisor. */ }
        return Map.of("commit", value);
    }
    @Transactional(readOnly = true)
    public SystemUpdateDto latest() { return queue.latest(); }
    @Transactional(readOnly = true)
    public SystemUpdateDto get(UUID id) { return queue.get(id); }

    @Scheduled(fixedDelayString = "${personal-agent.workers.system-delay:10000}")
    @Transactional
    public void notifyCompletedWorker() {
        if (properties.workers() != null && !properties.workers().enabled()) return;
        notifyCompletedOnce();
    }

    @Transactional
    public void notifyCompletedOnce() {
        for (SystemUpdateDto update : queue.completedWithoutNotification()) {
            String message = "Atualização ChiefAI " + update.id() + ": " + update.status()
                    + (update.errorSummary() == null ? "" : ". " + update.errorSummary());
            outbox.enqueue("LOCAL", null, com.chiefai.personalagent.shared.Jsonb.value(Map.of("text", message)), "system-update:" + update.id());
            if (properties.telegram() != null && properties.telegram().enabled() && owner.access().telegram() != null) {
                String chatId = owner.access().telegram().allowedChatId();
                if (chatId != null && !chatId.isBlank()) outbox.enqueueTelegram(chatId, message, "system-update-telegram:" + update.id());
            }
            queue.markNotified(update.id());
        }
    }
}
