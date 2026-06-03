package com.cryptopulse.wallet.repository;

import com.cryptopulse.wallet.domain.OrderReservationStatus;
import com.cryptopulse.wallet.domain.BalanceAuditRecord;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BalanceAuditRecordRepository extends JpaRepository<BalanceAuditRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from BalanceAuditRecord record where record.eventId = :eventId")
    Optional<BalanceAuditRecord> findByEventIdForUpdate(@Param("eventId") String eventId);

    Optional<BalanceAuditRecord> findByEventId(String eventId);

    List<BalanceAuditRecord> findAllByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderReservationStatus status);

    List<BalanceAuditRecord> findAllByStatusOrderByCreatedAtAsc(OrderReservationStatus status);
}
