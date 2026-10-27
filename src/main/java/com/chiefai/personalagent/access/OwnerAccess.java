package com.chiefai.personalagent.access;

public record OwnerAccess(boolean configured, String username, String passwordHash, Telegram telegram) {
    public record Telegram(String allowedUserId, String allowedChatId, String declaredPhoneNumber) {
        public boolean allows(String userId, String chatId) {
            boolean userMatches = allowedUserId != null && !allowedUserId.isBlank() && allowedUserId.equals(userId);
            boolean chatMatches = allowedChatId != null && !allowedChatId.isBlank() && allowedChatId.equals(chatId);
            return userMatches && chatMatches;
        }
    }
}
