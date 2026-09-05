package com.fundaro.zodiac.taurus.repository.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationPushDelivery;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushDeliveryType;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationPushDelivery d left join fetch d.notice where d.id = :id and d.deleted = false")
    Optional<NotificationPushDelivery> findByIdForUpdate(@Param("id") Long id);

    /** Lock senza join fetch: la console amministrativa non legge mai la notice collegata. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationPushDelivery d where d.id = :id and d.deleted = false")
    Optional<NotificationPushDelivery> findByIdForAdminUpdate(@Param("id") Long id);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationPushDelivery d left join fetch d.notice where d.deleted = false and d.userId = :userId and d.digestLocalDate = :date and d.deliveryType = :type and d.status = :status and d.nextAttemptAt <= :now")
    List<NotificationPushDelivery> findDigestForUpdate(
        @Param("userId") String userId,
        @Param("date") LocalDate date,
        @Param("type") NotificationPushDeliveryType type,
        @Param("status") NotificationStatus status,
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
