package com.chiefai.personalagent.task.infrastructure;

import com.chiefai.personalagent.task.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PersonalTaskJpaRepository extends JpaRepository<PersonalTaskEntity, UUID> {
    List<PersonalTaskEntity> findByStatusOrderByCreatedAtDesc(TaskStatus status);
    List<PersonalTaskEntity> findAllByOrderByCreatedAtDesc();
}
