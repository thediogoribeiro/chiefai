package com.chiefai.personalagent.orchestration.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentExecutionJpaRepository extends JpaRepository<AgentExecutionEntity, UUID> {
    List<AgentExecutionEntity> findByEventId(UUID eventId);
    long countByEventId(UUID eventId);
}
