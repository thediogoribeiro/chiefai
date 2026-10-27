package com.chiefai.personalagent.integration.telegram;

import com.chiefai.personalagent.access.OwnerAccess;
import com.chiefai.personalagent.access.OwnerAccessConfigurationLoader;
import com.chiefai.personalagent.ingestion.application.EventDtos;
import com.chiefai.personalagent.ingestion.application.IngestionService;
import com.chiefai.personalagent.ingestion.domain.EventSource;
import com.chiefai.personalagent.ingestion.domain.EventType;
import com.chiefai.personalagent.integration.openai.TranscriptionGateway;
import com.chiefai.personalagent.notification.application.NotificationOutboxService;
import com.chiefai.personalagent.shared.Jsonb;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/v1/integrations/telegram")
public class TelegramWebhookController {
    private final PersonalAgentProperties properties;
    private final OwnerAccessConfigurationLoader access;
    private final IngestionService ingestion;
    private final TranscriptionGateway transcription;
    private final NotificationOutboxService outbox;

    public TelegramWebhookController(PersonalAgentProperties properties, OwnerAccessConfigurationLoader access, IngestionService ingestion,
                                     TranscriptionGateway transcription, NotificationOutboxService outbox) {
        this.properties = properties;
        this.access = access;
        this.ingestion = ingestion;
        this.transcription = transcription;
        this.outbox = outbox;
    }

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Object webhook(@RequestBody JsonNode update, HttpServletRequest request) {
        if (properties.telegram() != null && properties.telegram().enabled()) {
            String expected = properties.telegram().webhookSecret();
            String provided = request.getHeader("X-Telegram-Bot-Api-Secret-Token");
            if (expected == null || expected.isBlank() || !expected.equals(provided)) {
                throw new SecurityException("Invalid Telegram webhook secret");
            }
        }
        JsonNode message = update.path("message");
        String updateId = update.path("update_id").asText(message.path("message_id").asText());
        String chatId = message.path("chat").path("id").asText("");
        String userId = message.path("from").path("id").asText("");
        if (!authorized(userId, chatId)) {
            if (!chatId.isBlank()) {
                outbox.enqueueTelegram(chatId, "ChiefAI está configurado para acesso privado.", "telegram-denied:" + updateId);
            }
            return java.util.Map.of("accepted", true);
        }
        String externalId = "telegram-" + updateId;
        OffsetDateTime occurredAt = message.hasNonNull("date")
                ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(message.get("date").asLong()), ZoneOffset.UTC)
                : OffsetDateTime.now(ZoneOffset.UTC);
        ObjectNode content = Jsonb.object();
        if (message.hasNonNull("text")) {
            content.put("kind", "TEXT");
            String text = normalizeCommand(message.get("text").asText());
            if (handleControlMessage(chatId, updateId, text)) {
                return java.util.Map.of("accepted", true);
            }
            content.put("text", text);
        } else if (message.has("voice") || message.has("audio")) {
            String fileId = message.has("voice") ? message.path("voice").path("file_id").asText() : message.path("audio").path("file_id").asText();
            content.put("kind", "AUDIO");
            content.put("fileReference", fileId);
            content.put("text", transcription.transcribe(fileId));
        } else {
            content.put("kind", "STRUCTURED_DATA");
            content.set("payload", message);
        }
        ObjectNode metadata = Jsonb.object();
        metadata.put("chatId", chatId);
        metadata.put("telegramUserId", userId);
        metadata.put("replyChannel", "TELEGRAM");
        return ingestion.accept(new EventDtos.CreateEventRequest(externalId, EventSource.TELEGRAM, EventType.MESSAGE_RECEIVED, occurredAt, content, metadata));
    }

    private boolean authorized(String userId, String chatId) {
        OwnerAccess owner = access.access();
        return owner != null && owner.telegram() != null && owner.telegram().allows(userId, chatId);
    }

    private boolean handleControlMessage(String chatId, String updateId, String text) {
        String lower = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("/start") || lower.startsWith("/start@")) {
            outbox.enqueueTelegram(chatId, "ChiefAI ligado. Usa /help para ver comandos disponíveis.", "telegram-control:" + updateId);
            return true;
        }
        if (lower.equals("/help") || lower.startsWith("/help@")) {
            outbox.enqueueTelegram(chatId, helpText(), "telegram-control:" + updateId);
            return true;
        }
        return false;
    }

    private String normalizeCommand(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("/finance")) {
            return "/personal [finance] " + trimmed.replaceFirst("^/\\S+\\s*", "");
        }
        return trimmed;
    }

    private String helpText() {
        return """
                ChiefAI comandos:
                /health <pergunta> - saúde e métricas guardadas
                /finance <pergunta> - finanças pessoais no dashboard
                /rucodel <pergunta> ou /work <pergunta> - contexto Rucodel
                /personal <pergunta> - tarefas e assuntos pessoais

                Também podes escrever em linguagem normal. O Manager decide o especialista certo.
                """;
    }
}
