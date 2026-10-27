package com.chiefai.personalagent.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "personal-agent")
public record PersonalAgentProperties(
        ApiKey apiKey,
        Workers workers,
        Routing routing,
        Knowledge knowledge,
        Access access,
        Telegram telegram,
        Google google,
        WhatsApp whatsapp,
        Connectors connectors,
        Expenses expenses,
        Finance finance,
        OpenAi openai
) {
    public record ApiKey(boolean enabled, String value) {
    }

    public record Workers(boolean enabled, int batchSize, int maxAttempts) {
    }

    public record Routing(int maxRoutes) {
    }

    public record Knowledge(int maxChars) {
    }

    public record Access(String file) {
    }

    public record Telegram(boolean enabled, String botToken, String botUsername, String webhookSecret) {
    }

    public record Google(boolean enabled, String clientId, String clientSecret, String redirectUri, String tokenEncryptionKey,
                         String expensesSpreadsheetId, String expensesRawSheet, String expensesReviewSheet,
                         String expensesDashboardSheet, String expensesMonthlySheet) {
    }

    public record WhatsApp(boolean enabled, String verifyToken, String accessToken, String appSecret) {
    }

    public record Connectors(boolean trelloEnabled, boolean teamsEnabled, boolean ouraEnabled) {
    }

    public record Expenses(Gmail gmail) {
    }

    public record Gmail(String inputLabel, String processedLabel, String reviewLabel, Duration pollingInterval) {
    }

    public record Finance(Export export) {
    }

    public record Export(int maxRows) {
    }

    public record OpenAi(boolean enabled, String apiKey, String model, String routerModel, String transcriptionModel, Duration timeout) {
    }
}
