package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.PushReminder;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PushReminderRepository extends JpaRepository<PushReminder, Long> {

    Page<PushReminder> findAllByDeletedFalseAndStatus(NotificationStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reminder from PushReminder reminder where reminder.id = :id and reminder.deleted = false")
    java.util.Optional<PushReminder> findByIdForUpdate(@Param("id") Long id);

    List<PushReminder> findByDeletedFalseAndSentFalseAndSendAtLessThanEqual(Instant now);

    long deleteAllByUserId(String userId);

    long deleteAllByEventIdAndUserIdAndSentFalse(Long eventId, String userId);

    long deleteAllByEventIdAndSentFalse(Long eventId);

    long deleteAllBySentTrueAndSendAtBefore(Instant cutoff);
}
