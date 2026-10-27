package com.chiefai.personalagent.integration.gmail;

public record GmailAttachmentRef(String partId, String filename, String mimeType, String contentHash, byte[] content) {
}
