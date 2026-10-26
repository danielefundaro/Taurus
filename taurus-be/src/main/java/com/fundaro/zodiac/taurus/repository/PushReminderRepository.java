package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.PushReminder;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.domain.notification.ReminderOrigin;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface PushReminderRepository extends JpaRepository<PushReminder, Long> {

    Page<PushReminder> findAllByDeletedFalseAndStatus(NotificationStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reminder from PushReminder reminder where reminder.id = :id and reminder.deleted = false")
    java.util.Optional<PushReminder> findByIdForUpdate(@Param("id") Long id);

    /** Gli ID dei promemoria da elaborare, un lotto per volta: il dispatcher prende ogni job per sé. */
    @Query("select reminder.id from PushReminder reminder where reminder.deleted = false"
        + " and reminder.status = :status and reminder.sendAt <= :now order by reminder.sendAt")
    List<Long> findReadyIds(@Param("status") NotificationStatus status, @Param("now") Instant now, Pageable pageable);


    long deleteAllByUserId(String userId);

    long deleteAllByEventIdAndUserIdAndStatus(Long eventId, String userId, NotificationStatus status);

    long deleteAllByEventIdAndStatus(Long eventId, NotificationStatus status);

    /** Promemoria chiusi da più del periodo di conservazione: restano fuori solo i pendenti. */
    long deleteAllByStatusNotAndSendAtBefore(NotificationStatus status, Instant cutoff);

    /** I promemoria ancora pendenti di un evento, per la ripianificazione selettiva. */
    List<PushReminder> findAllByEventIdAndUserIdAndStatusAndDeletedFalse(Long eventId, String userId, NotificationStatus status);

    /**
     * Cancella i soli promemoria pendenti che ereditano il valore predefinito: gli anticipi scelti
     * sull'occorrenza o sull'evento non vengono toccati dalla ripianificazione, e nemmeno i falliti.
     */
    long deleteAllByEventIdAndUserIdAndStatusAndReminderOriginIn(
        Long eventId, String userId, NotificationStatus status, Collection<ReminderOrigin> origins);
}
