package com.chiefai.personalagent.expenses.application;

import com.chiefai.personalagent.expenses.domain.ExpenseOwner;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class OwnerIdentifier {
    public ExpenseOwner identify(String text) {
        String normalized = normalize(text);
        if (contains(normalized, "jose diogo ribeiro")
                || contains(normalized, "diogo ribeiro")
                || contains(normalized, "jose diogo cabral castelo branco")) {
            return ExpenseOwner.DIOGO;
        }
        if (contains(normalized, "maria joao pacheco pontes")
                || contains(normalized, "maria pontes")) {
            return ExpenseOwner.MARIA;
        }
        if (contains(normalized, "cliente") || contains(normalized, "nif") || contains(normalized, "contribuinte")) {
            return ExpenseOwner.UNKNOWN;
        }
        return ExpenseOwner.UNKNOWN;
    }

    public boolean appearsOtherPerson(String text) {
        String normalized = normalize(text);
        return contains(normalized, "titular ana ") || contains(normalized, "titular joao ") || contains(normalized, "cliente ana ") || contains(normalized, "cliente joao ");
    }

    private boolean contains(String text, String candidate) {
        return text.contains(candidate);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
    }
}
