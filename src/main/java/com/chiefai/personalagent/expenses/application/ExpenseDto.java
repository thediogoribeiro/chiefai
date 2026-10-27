package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.domain.ExpenseCategory;
import com.chiefai.personalagent.expenses.domain.ExpenseOwner;
import com.chiefai.personalagent.expenses.domain.ExpenseSource;
import com.chiefai.personalagent.expenses.domain.ExpenseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ExpenseDto(UUID id, ExpenseStatus status, ExpenseSource source, String sourceMessageId,
                         String sourceAttachmentHash, OffsetDateTime receivedAt, LocalDate documentDate,
                         LocalDate dueDate, ExpenseOwner owner, String supplierName, String documentNumber,
                         String description, ExpenseCategory category, String subcategory, Boolean recurring,
                         String currency, BigDecimal grossAmount, double classificationConfidence,
                         double extractionConfidence, List<String> evidence, List<String> reviewReasons,
                         OffsetDateTime syncedAt, String lastError) {
}
