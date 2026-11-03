package com.chiefai.personalagent.system.infrastructure;

import com.chiefai.personalagent.system.domain.UpdateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SystemUpdateRepository extends JpaRepository<SystemUpdateEntity, UUID> {
    Optional<SystemUpdateEntity> findByActionId(UUID actionId);
    Optional<SystemUpdateEntity> findFirstByOrderByRequestedAtDesc();
    boolean existsByStatusIn(Collection<UpdateStatus> statuses);
    List<SystemUpdateEntity> findByNotifiedAtIsNullAndStatusIn(Collection<UpdateStatus> statuses);
}
