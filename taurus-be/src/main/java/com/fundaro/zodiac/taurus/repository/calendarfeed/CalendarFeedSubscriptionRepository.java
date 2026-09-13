package com.fundaro.zodiac.taurus.repository.calendarfeed;

import com.fundaro.zodiac.taurus.domain.calendarfeed.CalendarFeedStatus;
import com.fundaro.zodiac.taurus.domain.calendarfeed.CalendarFeedSubscription;
import com.fundaro.zodiac.taurus.domain.calendarfeed.CalendarFeedType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarFeedSubscriptionRepository extends JpaRepository<CalendarFeedSubscription, UUID> {
    Optional<CalendarFeedSubscription> findByIdAndDeletedFalse(UUID id);

    List<CalendarFeedSubscription> findAllByOwner_IdAndDeletedFalseOrderByInsertDateDesc(Long ownerId);

    List<CalendarFeedSubscription> findAllByDeletedFalseOrderByInsertDateDesc();

    long countByOwner_IdAndStatusAndDeletedFalse(Long ownerId, CalendarFeedStatus status);

    long countByFeedTypeAndStatusAndDeletedFalse(CalendarFeedType type, CalendarFeedStatus status);

    List<CalendarFeedSubscription> findAllByOwner_IdAndStatusAndDeletedFalse(Long ownerId, CalendarFeedStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CalendarFeedSubscription s where s.id = :id and s.deleted = false")
    Optional<CalendarFeedSubscription> findByIdForUpdate(@Param("id") UUID id);
}
