package com.chiefai.personalagent.expenses.infrastructure;

import com.chiefai.personalagent.expenses.application.ExpenseExtraction;
import com.chiefai.personalagent.expenses.domain.*;
import com.chiefai.personalagent.shared.InvalidStateException;
import com.chiefai.personalagent.shared.Jsonb;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "expense_record")
public class ExpenseRecordEntity {
    @Id
    private UUID id;
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseSource source;
    @Column(name = "source_message_id", nullable = false)
    private String sourceMessageId;
    @Column(name = "source_attachment_part_id")
    private String sourceAttachmentPartId;
    @Column(name = "source_attachment_hash", nullable = false)
    private String sourceAttachmentHash;
    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;
    @Column(name = "document_date")
    private LocalDate documentDate;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseOwner owner;
    @Column(name = "supplier_name")
    private String supplierName;
    @Column(name = "invoice_or_receipt_number")
    private String invoiceOrReceiptNumber;
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseCategory category;
    private String subcategory;
    private Boolean recurring;
    @Column(nullable = false)
    private String currency;
    @Column(name = "gross_amount", precision = 19, scale = 4)
    private BigDecimal grossAmount;
    @Column(name = "classification_confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal classificationConfidence;
    @Column(name = "extraction_confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal extractionConfidence;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode evidence;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseStatus status;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_reasons", nullable = false, columnDefinition = "jsonb")
    private JsonNode reviewReasons;
    @Column(name = "spreadsheet_row_number")
    private Integer spreadsheetRowNumber;
    @Column(name = "synced_at")
    private OffsetDateTime syncedAt;
    @Column(name = "last_error")
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Version
    private long version;

    public ExpenseRecordEntity() {
    }

    public static ExpenseRecordEntity create(String idempotencyKey, ExpenseSource source, String messageId, String attachmentPartId,
                                             String attachmentHash, OffsetDateTime receivedAt, String description, ExpenseExtraction extraction,
                                             List<String> reviewReasons, ExpenseStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        ExpenseRecordEntity entity = new ExpenseRecordEntity();
        entity.id = UUID.randomUUID();
        entity.idempotencyKey = idempotencyKey;
        entity.source = source;
        entity.sourceMessageId = messageId;
        entity.sourceAttachmentPartId = attachmentPartId;
        entity.sourceAttachmentHash = attachmentHash;
        entity.receivedAt = receivedAt == null ? now : receivedAt;
        entity.description = description;
        entity.applyExtraction(extraction, reviewReasons, status);
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void applyExtraction(ExpenseExtraction extraction, List<String> reasons, ExpenseStatus newStatus) {
        this.documentDate = extraction.documentDate();
        this.dueDate = extraction.dueDate();
        this.owner = extraction.owner();
        this.supplierName = extraction.supplierName();
        this.invoiceOrReceiptNumber = extraction.invoiceOrReceiptNumber();
        this.category = extraction.category();
        this.subcategory = extraction.subcategory();
        this.recurring = extraction.recurring();
        this.currency = extraction.currency() == null ? "EUR" : extraction.currency();
        this.grossAmount = extraction.grossAmount();
        this.classificationConfidence = BigDecimal.valueOf(Math.max(0, Math.min(1, extraction.confidence())));
        this.extractionConfidence = BigDecimal.valueOf(Math.max(0, Math.min(1, extraction.confidence())));
        this.evidence = Jsonb.value(extraction.evidence() == null ? List.of() : extraction.evidence());
        this.reviewReasons = Jsonb.value(reasons == null ? List.of() : reasons);
        this.status = newStatus;
        this.updatedAt = OffsetDateTime.now();
    }

    public void approve(boolean autoPublish) {
        if (status == ExpenseStatus.IGNORED_OTHER_OWNER) {
            throw new InvalidStateException("Ignored expenses cannot be approved");
        }
        status = autoPublish ? ExpenseStatus.SYNC_PENDING : ExpenseStatus.APPROVED;
        updatedAt = OffsetDateTime.now();
    }

    public void approveForDashboard() {
        if (status == ExpenseStatus.IGNORED_OTHER_OWNER) {
            throw new InvalidStateException("Ignored expenses cannot be approved");
        }
        status = ExpenseStatus.APPROVED;
        lastError = null;
        updatedAt = OffsetDateTime.now();
    }

    public void reject(String reason) {
        status = ExpenseStatus.FAILED;
        lastError = reason == null || reason.isBlank() ? "Rejected" : reason;
        updatedAt = OffsetDateTime.now();
    }

    public void markSyncPending() {
        if (status == ExpenseStatus.SYNCED) {
            return;
        }
        status = ExpenseStatus.SYNC_PENDING;
        updatedAt = OffsetDateTime.now();
    }

    public void retryProcessing() {
        if (status != ExpenseStatus.FAILED) {
            throw new InvalidStateException("Only failed expenses can be retried");
        }
        status = ExpenseStatus.REVIEW_REQUIRED;
        lastError = null;
        updatedAt = OffsetDateTime.now();
    }

    public void correct(ExpenseOwner owner, ExpenseCategory category, String subcategory, String description) {
        if (owner != null) {
            this.owner = owner;
        }
        if (category != null) {
            this.category = category;
        }
        if (subcategory != null) {
            this.subcategory = subcategory.isBlank() ? null : subcategory;
        }
        if (description != null) {
            this.description = description.isBlank() ? null : description;
        }
        if (status == ExpenseStatus.REVIEW_REQUIRED && this.owner != ExpenseOwner.UNKNOWN && grossAmount != null) {
            status = ExpenseStatus.EXTRACTED;
            reviewReasons = Jsonb.value(List.of());
        }
        updatedAt = OffsetDateTime.now();
    }

    public void markSynced(Integer rowNumber) {
        status = ExpenseStatus.SYNCED;
        spreadsheetRowNumber = rowNumber;
        syncedAt = OffsetDateTime.now();
        updatedAt = syncedAt;
        lastError = null;
    }

    public void markFailed(String error) {
        status = ExpenseStatus.FAILED;
        lastError = error;
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public ExpenseSource getSource() { return source; }
    public String getSourceMessageId() { return sourceMessageId; }
    public String getSourceAttachmentPartId() { return sourceAttachmentPartId; }
    public String getSourceAttachmentHash() { return sourceAttachmentHash; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public LocalDate getDocumentDate() { return documentDate; }
    public LocalDate getDueDate() { return dueDate; }
    public ExpenseOwner getOwner() { return owner; }
    public String getSupplierName() { return supplierName; }
    public String getInvoiceOrReceiptNumber() { return invoiceOrReceiptNumber; }
    public String getDescription() { return description; }
    public ExpenseCategory getCategory() { return category; }
    public String getSubcategory() { return subcategory; }
    public Boolean getRecurring() { return recurring; }
    public String getCurrency() { return currency; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getClassificationConfidence() { return classificationConfidence; }
    public BigDecimal getExtractionConfidence() { return extractionConfidence; }
    public JsonNode getEvidence() { return evidence; }
    public ExpenseStatus getStatus() { return status; }
    public JsonNode getReviewReasons() { return reviewReasons; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getSyncedAt() { return syncedAt; }
    public String getLastError() { return lastError; }
}
