package com.fundaro.zodiac.taurus.repository.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationPushDelivery;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushDeliveryType;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationPushDeliveryRepository extends JpaRepository<NotificationPushDelivery, Long> {

    Page<NotificationPushDelivery> findAllByDeletedFalseAndStatus(NotificationStatus status, Pageable pageable);

    boolean existsBySourceEventKeyAndUserIdAndDeliveryTypeAndDeletedFalse(
        String sourceEventKey,
        String userId,
        NotificationPushDeliveryType deliveryType
    );

    boolean existsByNoticeIdAndSnoozeRevisionAndDeliveryTypeAndDeletedFalse(
        Long noticeId,
        Integer snoozeRevision,
        NotificationPushDeliveryType deliveryType
    );

    @Query("select d.id from NotificationPushDelivery d where d.deleted = false and d.status = :status and d.nextAttemptAt <= :now order by d.nextAttemptAt, d.id")
    List<Long> findReadyIds(@Param("status") NotificationStatus status, @Param("now") ZonedDateTime now, Pageable pageable);

    /**
     * Prende il job per questa replica sola. Il lock sta nella stessa istruzione che legge la riga e
     * {@code skip locked} la lascia a chi la tiene già, invece di far attendere l'altro worker per poi
     * fargli fallire la presa in carico. Senza join fetch: con un join esterno PostgreSQL non accetta
     * {@code for update} e Hibernate ricadrebbe su un lock separato per id e versione, che dopo la
     * consegna dell'altro worker solleva {@code StaleObjectStateException}. La notice è pigra e si
     * carica da sola nella stessa transazione.
     */
    @Query(value = "select * from notification_push_delivery where id = :id and deleted = false for update skip locked", nativeQuery = true)
    Optional<NotificationPushDelivery> claimForUpdate(@Param("id") Long id);

    /**
     * Lock senza join fetch: la console amministrativa non legge mai la notice collegata.
     */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationPushDelivery d where d.id = :id and d.deleted = false")
    Optional<NotificationPushDelivery> findByIdForAdminUpdate(@Param("id") Long id);

    /** Il riepilogo del giorno, preso con lo stesso criterio di {@link #claimForUpdate(Long)}. */
    @Query(
        value = "select * from notification_push_delivery" +
            " where deleted = false and user_id = :userId and digest_local_date = :date" +
            " and delivery_type = :type and status = :status and next_attempt_at <= :now" +
            " order by id for update skip locked",
        nativeQuery = true
    )
    List<NotificationPushDelivery> claimDigestForUpdate(
        @Param("userId") String userId,
        @Param("date") LocalDate date,
        @Param("type") String type,
        @Param("status") String status,
        @Param("now") ZonedDateTime now
    );

    List<NotificationPushDelivery> findAllByNoticeIdAndDeliveryTypeAndStatusAndDeletedFalse(
        Long noticeId,
        NotificationPushDeliveryType deliveryType,
        NotificationStatus status
    );

    long deleteAllByUserId(String userId);

    long deleteAllByStatusAndDeliveredAtBefore(NotificationStatus status, ZonedDateTime cutoff);

    long deleteAllByStatusAndEditDateBefore(NotificationStatus status, ZonedDateTime cutoff);
}
