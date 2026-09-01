package com.fundaro.zodiac.taurus.repository.finance;

import com.fundaro.zodiac.taurus.domain.finance.FinanceNotificationOutbox;
import com.fundaro.zodiac.taurus.domain.finance.FinanceNotificationStatus;
import jakarta.persistence.LockModeType;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinanceNotificationOutboxRepository extends JpaRepository<FinanceNotificationOutbox, Long> {
    @Query("select event.id from FinanceNotificationOutbox event where event.deleted = false and event.status = :status and event.nextAttemptAt <= :now order by event.id")
    List<Long> findReadyIds(@Param("status") FinanceNotificationStatus status, @Param("now") ZonedDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from FinanceNotificationOutbox event where event.id = :id and event.deleted = false")
    Optional<FinanceNotificationOutbox> findByIdForUpdate(@Param("id") Long id);

    long deleteAllByStatusAndDeliveredAtBefore(FinanceNotificationStatus status, ZonedDateTime cutoff);
}
