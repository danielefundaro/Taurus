package com.fundaro.zodiac.taurus.repository.calendarfeed;

import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CalendarFeedSubscriptionRepository extends JpaRepository<CalendarFeedSubscription, UUID> {
    List<CalendarFeedSubscription> findAllByOwner_IdOrderByCreatedAtDesc(Long ownerId);
    List<CalendarFeedSubscription> findAllByOrderByCreatedAtDesc();
    long countByOwner_IdAndStatus(Long ownerId, CalendarFeedStatus status);
    long countByFeedTypeAndStatus(CalendarFeedType type, CalendarFeedStatus status);
    List<CalendarFeedSubscription> findAllByOwner_IdAndStatus(Long ownerId, CalendarFeedStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CalendarFeedSubscription s where s.id = :id")
    Optional<CalendarFeedSubscription> findByIdForUpdate(@Param("id") UUID id);
}
