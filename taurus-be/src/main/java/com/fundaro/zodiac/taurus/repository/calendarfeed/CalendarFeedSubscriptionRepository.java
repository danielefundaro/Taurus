package com.fundaro.zodiac.taurus.repository.calendarfeed;

import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

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
