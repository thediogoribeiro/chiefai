package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.domain.ExpenseOwner;
import com.chiefai.personalagent.expenses.domain.ExpenseSource;
import com.chiefai.personalagent.expenses.domain.ExpenseStatus;
import com.chiefai.personalagent.expenses.infrastructure.ExpenseRecordEntity;
import com.chiefai.personalagent.expenses.infrastructure.ExpenseRecordJpaRepository;
import com.chiefai.personalagent.expenses.infrastructure.FinanceDashboardUpdateEntity;
import com.chiefai.personalagent.expenses.infrastructure.FinanceDashboardUpdateJpaRepository;
import com.chiefai.personalagent.integration.gmail.GmailAttachmentRef;
import com.chiefai.personalagent.integration.gmail.GmailConnector;
import com.chiefai.personalagent.integration.gmail.GmailMessage;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class ExpenseIngestionService {
    private final GmailConnector gmail;
    private final ExpenseExtractionService extractionService;
    private final ExpenseRecordJpaRepository expenses;
    private final FinanceDashboardUpdateJpaRepository dashboardUpdates;
    private final PersonalAgentProperties properties;
    private final TransactionTemplate transactionTemplate;

    public ExpenseIngestionService(GmailConnector gmail, ExpenseExtractionService extractionService, ExpenseRecordJpaRepository expenses,
                                   FinanceDashboardUpdateJpaRepository dashboardUpdates, PersonalAgentProperties properties, TransactionTemplate transactionTemplate) {
        this.gmail = gmail;
        this.extractionService = extractionService;
        this.expenses = expenses;
        this.dashboardUpdates = dashboardUpdates;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${personal-agent.expenses.gmail.polling-interval:PT5M}")
    public void pollGmailExpensesWorker() {
        if (properties.workers() != null && !properties.workers().enabled()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> pollOnce());
    }

    @Transactional
    public int pollOnce() {
        String inputLabel = properties.expenses().gmail().inputLabel();
        int processed = 0;
        for (GmailMessage message : gmail.findMessagesByLabel(inputLabel, 25)) {
            if (!message.labels().contains(inputLabel)) {
                continue;
            }
            processMessage(message);
            processed++;
        }
        return processed;
    }

    @Transactional
    public ExpenseDto processMessage(GmailMessage message) {
        GmailAttachmentRef attachment = message.attachments() == null || message.attachments().isEmpty() ? null : message.attachments().getFirst();
        String extractedText = message.bodyText() + "\n" + extractAttachmentText(attachment);
        String hash = attachment == null ? sha256(extractedText) : attachment.contentHash();
        String partId = attachment == null ? "body" : attachment.partId();
        String key = message.messageId() + ":" + partId + ":" + hash;
        return expenses.findByIdempotencyKey(key)
                .map(ExpenseMapper::toDto)
                .orElseGet(() -> createExpense(message, partId, hash, key, extractedText));
    }

    private ExpenseDto createExpense(GmailMessage message, String partId, String hash, String key, String extractedText) {
        ExpenseExtraction extraction = extractionService.extract(extractedText);
        ExpenseReview review = extractionService.reviewFor(extraction);
        ExpenseStatus status;
        if (extraction.owner() == ExpenseOwner.OTHER) {
            status = ExpenseStatus.IGNORED_OTHER_OWNER;
            gmail.addLabel(message.messageId(), properties.expenses().gmail().processedLabel());
        } else if (review.required()) {
            status = ExpenseStatus.REVIEW_REQUIRED;
            gmail.addLabel(message.messageId(), properties.expenses().gmail().reviewLabel());
        } else {
            status = ExpenseStatus.EXTRACTED;
            gmail.addLabel(message.messageId(), properties.expenses().gmail().processedLabel());
        }
        ExpenseRecordEntity entity = ExpenseRecordEntity.create(key, ExpenseSource.GMAIL, message.messageId(), partId, hash,
                message.receivedAt() == null ? OffsetDateTime.now() : message.receivedAt(), safeDescription(message), extraction, review.reasons(), status);
        ExpenseRecordEntity saved = expenses.save(entity);
        publishDashboardUpdateAfterCommit(saved.getId());
        return ExpenseMapper.toDto(saved);
    }

    private void publishDashboardUpdateAfterCommit(java.util.UUID expenseId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dashboardUpdates.save(new FinanceDashboardUpdateEntity("EXPENSE_CREATED", expenseId));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dashboardUpdates.save(new FinanceDashboardUpdateEntity("EXPENSE_CREATED", expenseId));
            }
        });
    }

    private String safeDescription(GmailMessage message) {
        String subject = message.subject() == null ? "Despesa Gmail" : message.subject();
        return subject.length() > 500 ? subject.substring(0, 500) : subject;
    }

    private String extractAttachmentText(GmailAttachmentRef attachment) {
        if (attachment == null || attachment.content() == null) {
            return "";
        }
        if ("application/pdf".equalsIgnoreCase(attachment.mimeType())) {
            try (var document = Loader.loadPDF(attachment.content())) {
                return new PDFTextStripper().getText(document);
            } catch (Exception e) {
                return "";
            }
        }
        if (attachment.mimeType() != null && attachment.mimeType().startsWith("text/")) {
            return new String(attachment.content(), StandardCharsets.UTF_8);
        }
        return "";
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash expense source", e);
        }
    }
}
