package com.chiefai.personalagent.expenses.infrastructure;

import com.chiefai.personalagent.shared.Jsonb;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "expense_audit_log")
public class ExpenseAuditLogEntity {
    @Id
    private UUID id;
    @Column(name = "expense_id")
    private UUID expenseId;
    @Column(nullable = false)
    private String action;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode details;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public ExpenseAuditLogEntity() {
    }

    public ExpenseAuditLogEntity(UUID expenseId, String action, Map<String, ?> details) {
        this.id = UUID.randomUUID();
        this.expenseId = expenseId;
        this.action = action;
        this.details = Jsonb.value(details == null ? Map.of() : details);
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getExpenseId() { return expenseId; }
    public String getAction() { return action; }
    public JsonNode getDetails() { return details; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
