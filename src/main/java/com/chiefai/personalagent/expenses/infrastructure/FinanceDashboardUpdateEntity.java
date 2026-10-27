package com.chiefai.personalagent.expenses.infrastructure;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "finance_dashboard_update")
public class FinanceDashboardUpdateEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String reason;
    @Column(name = "expense_id")
    private UUID expenseId;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public FinanceDashboardUpdateEntity() {
    }

    public FinanceDashboardUpdateEntity(String reason, UUID expenseId) {
        this.id = UUID.randomUUID();
        this.reason = reason;
        this.expenseId = expenseId;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getReason() { return reason; }
    public UUID getExpenseId() { return expenseId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
