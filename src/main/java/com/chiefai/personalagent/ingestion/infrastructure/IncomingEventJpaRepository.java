package com.chiefai.personalagent.ingestion.infrastructure;

import com.chiefai.personalagent.ingestion.domain.EventSource;
import com.chiefai.personalagent.ingestion.domain.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncomingEventJpaRepository extends JpaRepository<IncomingEventEntity, UUID> {
    Optional<IncomingEventEntity> findBySourceAndExternalId(EventSource source, String externalId);
    List<IncomingEventEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from IncomingEventEntity e where (e.status = :received or (e.status = :failed and (e.nextAttemptAt is null or e.nextAttemptAt <= :now))) order by e.createdAt asc")
    List<IncomingEventEntity> lockProcessable(EventStatus received, EventStatus failed, OffsetDateTime now, Pageable pageable);
}
