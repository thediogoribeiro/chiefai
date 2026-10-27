package com.chiefai.personalagent.expenses.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FinanceDashboardUpdateJpaRepository extends JpaRepository<FinanceDashboardUpdateEntity, UUID> {
    Optional<FinanceDashboardUpdateEntity> findFirstByOrderByCreatedAtDesc();
}
