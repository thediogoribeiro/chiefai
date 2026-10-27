package com.chiefai.personalagent.action.infrastructure;

import com.chiefai.personalagent.action.domain.ActionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProposedActionJpaRepository extends JpaRepository<ProposedActionEntity, UUID> {
    List<ProposedActionEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Optional<ProposedActionEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ProposedActionEntity a where a.status = :status and (a.nextAttemptAt is null or a.nextAttemptAt <= :now) order by a.createdAt asc")
    List<ProposedActionEntity> lockApproved(ActionStatus status, OffsetDateTime now, Pageable pageable);
}
