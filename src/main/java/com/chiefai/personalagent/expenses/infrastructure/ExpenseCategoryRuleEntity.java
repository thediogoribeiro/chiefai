package com.chiefai.personalagent.expenses.infrastructure;

import com.chiefai.personalagent.expenses.domain.ExpenseCategory;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "expense_category_rule")
public class ExpenseCategoryRuleEntity {
    @Id
    private UUID id;
    private int priority;
    @Column(name = "supplier_pattern")
    private String supplierPattern;
    @Column(name = "description_pattern")
    private String descriptionPattern;
    @Enumerated(EnumType.STRING)
    private ExpenseCategory category;
    private String subcategory;
    private Boolean recurring;
    private boolean enabled;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    @Version
    private long version;

    public UUID getId() { return id; }
    public int getPriority() { return priority; }
    public String getSupplierPattern() { return supplierPattern; }
    public String getDescriptionPattern() { return descriptionPattern; }
    public ExpenseCategory getCategory() { return category; }
    public String getSubcategory() { return subcategory; }
    public Boolean getRecurring() { return recurring; }
    public boolean isEnabled() { return enabled; }
}
