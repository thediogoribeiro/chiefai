package com.chiefai.personalagent.health.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyHealthSnapshotJpaRepository extends JpaRepository<DailyHealthSnapshotEntity, UUID> {
    Optional<DailyHealthSnapshotEntity> findByDateAndSource(LocalDate date, String source);
    List<DailyHealthSnapshotEntity> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}
