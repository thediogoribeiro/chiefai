package com.chiefai.personalagent.notification.infrastructure;

import com.chiefai.personalagent.notification.domain.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationOutboxJpaRepository extends JpaRepository<NotificationOutboxEntity, UUID> {
    Optional<NotificationOutboxEntity> findByIdempotencyKey(String idempotencyKey);
    List<NotificationOutboxEntity> findByStatus(NotificationStatus status);

    @Query("select n from NotificationOutboxEntity n where n.status in :statuses and (n.nextAttemptAt is null or n.nextAttemptAt <= :now) order by n.createdAt asc")
    List<NotificationOutboxEntity> findProcessable(List<NotificationStatus> statuses, OffsetDateTime now);
}
