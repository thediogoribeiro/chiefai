package com.chiefai.personalagent.expenses.infrastructure;

import com.chiefai.personalagent.expenses.domain.ExpenseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRecordJpaRepository extends JpaRepository<ExpenseRecordEntity, UUID>, JpaSpecificationExecutor<ExpenseRecordEntity> {
    Optional<ExpenseRecordEntity> findByIdempotencyKey(String idempotencyKey);
    List<ExpenseRecordEntity> findAllByOrderByReceivedAtDesc(Pageable pageable);
    List<ExpenseRecordEntity> findByDocumentDateBetweenOrderByDocumentDateAsc(LocalDate from, LocalDate to);
    List<ExpenseRecordEntity> findByStatus(ExpenseStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExpenseRecordEntity e where e.status = :status order by e.updatedAt asc")
    List<ExpenseRecordEntity> lockByStatus(ExpenseStatus status, Pageable pageable);

    long countByStatus(ExpenseStatus status);

    @Query("select max(e.updatedAt) from ExpenseRecordEntity e")
    Optional<OffsetDateTime> findLastUpdatedAt();
}
