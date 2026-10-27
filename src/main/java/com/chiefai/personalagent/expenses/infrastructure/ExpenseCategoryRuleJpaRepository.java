package com.chiefai.personalagent.expenses.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseCategoryRuleJpaRepository extends JpaRepository<ExpenseCategoryRuleEntity, UUID> {
    List<ExpenseCategoryRuleEntity> findByEnabledTrueOrderByPriorityAsc();
}
