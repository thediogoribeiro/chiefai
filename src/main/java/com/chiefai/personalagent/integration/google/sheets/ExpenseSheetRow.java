package com.chiefai.personalagent.integration.google.sheets;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExpenseSheetRow(UUID expenseId, String sourceMessageId, String sourceAttachmentHash, OffsetDateTime receivedAt,
                              LocalDate documentDate, LocalDate dueDate, String owner, String supplier,
                              String documentNumber, String description, String category, String subcategory,
                              Boolean recurring, String currency, BigDecimal grossAmount, double classificationConfidence,
                              String reviewStatus, OffsetDateTime syncedAt) {
}
