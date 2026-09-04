package com.fundaro.zodiac.taurus.repository.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.service.notification.NotificationPendingSummary;
import com.fundaro.zodiac.taurus.repository.projection.NotificationFailureProjection;
import jakarta.persistence.LockModeType;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    @Query("select event.id from NotificationOutbox event where event.deleted = false and event.status = :status and event.nextAttemptAt <= :now order by event.id")
    List<Long> findReadyIds(@Param("status") NotificationStatus status, @Param("now") ZonedDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from NotificationOutbox event where event.id = :id and event.deleted = false")
    Optional<NotificationOutbox> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        select event.source as source, count(event) as pendingCount, min(event.occurredAt) as oldestOccurredAt
        from NotificationOutbox event
        where event.deleted = false and event.status = :status
        group by event.source
        """)
    List<NotificationPendingSummary> summarizeByStatus(@Param("status") NotificationStatus status);

    boolean existsByEventKey(String eventKey);

    long deleteAllByStatusAndDeliveredAtBefore(NotificationStatus status, ZonedDateTime cutoff);

    @Query("""
        select count(event.id) as failureCount, min(event.occurredAt) as oldestOccurredAt
        from NotificationOutbox event
        where event.deleted = false and event.status = :status
        """)
    NotificationFailureProjection summarizeFailures(@Param("status") NotificationStatus status);

    Page<NotificationOutbox> findAllByDeletedFalseAndStatus(NotificationStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from NotificationOutbox event where event.deleted = false and event.id in :ids order by event.id")
    List<NotificationOutbox> findAllByIdsForUpdate(@Param("ids") List<Long> ids);
}
