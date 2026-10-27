package com.chiefai.personalagent.integration.gmail;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public record GmailMessage(String messageId, String threadId, OffsetDateTime receivedAt, String subject, String from,
                           String bodyText, Set<String> labels, List<GmailAttachmentRef> attachments) {
}
