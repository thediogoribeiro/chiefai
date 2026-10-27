package com.chiefai.personalagent.expenses.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseAuditLogJpaRepository extends JpaRepository<ExpenseAuditLogEntity, UUID> {
    List<ExpenseAuditLogEntity> findByExpenseIdOrderByCreatedAtDesc(UUID expenseId);
}
