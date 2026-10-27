package com.chiefai.personalagent.action.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ToolExecutionJpaRepository extends JpaRepository<ToolExecutionEntity, UUID> {
    Optional<ToolExecutionEntity> findByIdempotencyKey(String idempotencyKey);
}
