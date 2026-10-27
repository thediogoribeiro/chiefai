package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.domain.ExpenseCategory;
import com.chiefai.personalagent.expenses.domain.ExpenseOwner;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExpenseExtractionService {
    private static final Pattern MONEY = Pattern.compile("(?:total|valor|montante|gross|amount)\\s*[:\\-]?\\s*([0-9]+(?:[,.][0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern SUPPLIER = Pattern.compile("(?:fornecedor|supplier|entidade)\\s*[:\\-]\\s*([^\\n\\r]+)", Pattern.CASE_INSENSITIVE);
    private final OwnerIdentifier ownerIdentifier;
    private final ExpenseCategoryService categoryService;

    public ExpenseExtractionService(OwnerIdentifier ownerIdentifier, ExpenseCategoryService categoryService) {
        this.ownerIdentifier = ownerIdentifier;
        this.categoryService = categoryService;
    }

    public ExpenseExtraction extract(String text) {
        ExpenseOwner owner = ownerIdentifier.appearsOtherPerson(text) ? ExpenseOwner.OTHER : ownerIdentifier.identify(text);
        String supplier = extractSupplier(text);
        BigDecimal amount = extractAmount(text);
        LocalDate documentDate = extractDate(text);
        ExpenseClassification classification = categoryService.classify(supplier, text);
        double confidence = baseConfidence(owner, amount, classification);
        return new ExpenseExtraction(supplier, extractDocumentNumber(text), documentDate, null, "EUR", amount, owner,
                classification.category(), classification.subcategory(), classification.recurring(), confidence,
                List.of("titular=" + owner, "categoria=" + classification.strategy()));
    }

    public ExpenseReview reviewFor(ExpenseExtraction extraction) {
        java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
        if (extraction.owner() == ExpenseOwner.UNKNOWN) {
            reasons.add("titular desconhecido");
        }
        if (extraction.grossAmount() == null || extraction.grossAmount().compareTo(BigDecimal.ZERO) <= 0) {
            reasons.add("valor ausente ou inválido");
        }
        if (!"EUR".equalsIgnoreCase(extraction.currency())) {
            reasons.add("moeda não suportada");
        }
        if (extraction.confidence() < 0.7) {
            reasons.add("baixa confiança");
        }
        if (extraction.category() == null || extraction.category() == ExpenseCategory.OTHER && extraction.subcategory() == null) {
            reasons.add("categoria não determinada");
        }
        return new ExpenseReview(!reasons.isEmpty(), reasons);
    }

    private double baseConfidence(ExpenseOwner owner, BigDecimal amount, ExpenseClassification classification) {
        double score = 0.45;
        if (owner == ExpenseOwner.DIOGO || owner == ExpenseOwner.MARIA || owner == ExpenseOwner.OTHER) {
            score += 0.25;
        }
        if (amount != null) {
            score += 0.2;
        }
        if (classification.confidence() > 0.9) {
            score += 0.1;
        }
        return Math.min(1, score);
    }

    private String extractSupplier(String text) {
        Matcher matcher = SUPPLIER.matcher(text == null ? "" : text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return text == null || text.isBlank() ? "Fornecedor desconhecido" : text.lines().findFirst().orElse("Fornecedor desconhecido").trim();
    }

    private BigDecimal extractAmount(String text) {
        Matcher matcher = MONEY.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return null;
        }
        return new BigDecimal(matcher.group(1).replace(',', '.'));
    }

    private LocalDate extractDate(String text) {
        Matcher matcher = DATE.matcher(text == null ? "" : text);
        return matcher.find() ? LocalDate.parse(matcher.group(1), DateTimeFormatter.ISO_LOCAL_DATE) : null;
    }

    private String extractDocumentNumber(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Matcher matcher = Pattern.compile("(?:fatura|recibo|invoice)\\s*(?:n[oº.]*)?\\s*[:\\-]?\\s*([a-z0-9\\-/]+)", Pattern.CASE_INSENSITIVE).matcher(lower);
        return matcher.find() ? matcher.group(1) : null;
    }
}
