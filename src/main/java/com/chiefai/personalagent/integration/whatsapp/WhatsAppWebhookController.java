package com.chiefai.personalagent.integration.whatsapp;

import com.chiefai.personalagent.ingestion.application.EventDtos;
import com.chiefai.personalagent.ingestion.application.IngestionService;
import com.chiefai.personalagent.ingestion.domain.EventSource;
import com.chiefai.personalagent.ingestion.domain.EventType;
import com.chiefai.personalagent.integration.openai.TranscriptionGateway;
import com.chiefai.personalagent.shared.Jsonb;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/v1/integrations/whatsapp")
public class WhatsAppWebhookController {
    private final PersonalAgentProperties properties;
    private final IngestionService ingestion;
    private final TranscriptionGateway transcription;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookController(PersonalAgentProperties properties, IngestionService ingestion, TranscriptionGateway transcription, ObjectMapper objectMapper) {
        this.properties = properties;
        this.ingestion = ingestion;
        this.transcription = transcription;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/webhook")
    String verify(@RequestParam(name = "hub.mode", required = false) String mode,
                  @RequestParam(name = "hub.verify_token", required = false) String token,
                  @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!properties.whatsapp().enabled()) {
            throw new SecurityException("WhatsApp integration is disabled");
        }
        if ("subscribe".equals(mode) && properties.whatsapp().verifyToken().equals(token)) {
            return challenge;
        }
        throw new SecurityException("Invalid WhatsApp verify token");
    }

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.ACCEPTED)
    EventDtos.EventResponse webhook(@RequestBody String rawBody, HttpServletRequest request) throws Exception {
        if (properties.whatsapp().enabled()) {
            verifySignature(rawBody, request.getHeader("X-Hub-Signature-256"));
        }
        JsonNode update = objectMapper.readTree(rawBody);
        JsonNode message = update.at("/entry/0/changes/0/value/messages/0");
        String messageId = message.path("id").asText("whatsapp-" + System.nanoTime());
        ObjectNode content = Jsonb.object();
        if (message.has("text")) {
            content.put("kind", "TEXT");
            content.put("text", message.path("text").path("body").asText(""));
        } else if (message.has("audio")) {
            String mediaId = message.path("audio").path("id").asText("");
            content.put("kind", "AUDIO");
            content.put("fileReference", mediaId);
            content.put("text", transcription.transcribe(mediaId));
        } else {
            content.put("kind", "STRUCTURED_DATA");
            content.set("payload", message);
        }
        return ingestion.accept(new EventDtos.CreateEventRequest("whatsapp-" + messageId, EventSource.WHATSAPP, EventType.MESSAGE_RECEIVED,
                OffsetDateTime.now(), content, Jsonb.object()));
    }

    private void verifySignature(String rawBody, String signatureHeader) throws Exception {
        String secret = properties.whatsapp().appSecret();
        if (secret == null || secret.isBlank() || signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            throw new SecurityException("Missing WhatsApp signature");
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signatureHeader.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid WhatsApp signature");
        }
    }
}
