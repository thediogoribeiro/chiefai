package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.infrastructure.ExpenseRecordEntity;
import com.chiefai.personalagent.shared.Jsonb;

import java.util.List;

public final class ExpenseMapper {
    private ExpenseMapper() {
    }

    public static ExpenseDto toDto(ExpenseRecordEntity e) {
        return new ExpenseDto(e.getId(), e.getStatus(), e.getSource(), e.getSourceMessageId(), e.getSourceAttachmentHash(),
                e.getReceivedAt(), e.getDocumentDate(), e.getDueDate(), e.getOwner(), e.getSupplierName(),
                e.getInvoiceOrReceiptNumber(), e.getDescription(), e.getCategory(), e.getSubcategory(), e.getRecurring(),
                e.getCurrency(), e.getGrossAmount(), e.getClassificationConfidence().doubleValue(),
                e.getExtractionConfidence().doubleValue(), list(e.getEvidence()), list(e.getReviewReasons()),
                e.getSyncedAt(), e.getLastError());
    }

    private static List<String> list(com.fasterxml.jackson.databind.JsonNode node) {
        return Jsonb.MAPPER.convertValue(node, Jsonb.MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
    }
}
