package com.fundaro.zodiac.taurus.repository.calendarfeed;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CalendarFeedEventRepository extends JpaRepository<CalendarEvents, Long> {
    @Query("""
        select e.calendarUid as uid, e.calendarSequence as sequence,
               e.calendarFeedModifiedAt as modifiedAt, e.startDate as startAt,
               e.endDate as endAt, e.name as summary, e.location as location,
               e.description as description, e.id as eventId
        from CalendarEvents e
        where e.deleted = false and (e.seriesExcluded = false or e.seriesExcluded is null)
          and e.state in :states and e.endDate >= :from and e.startDate <= :to
        order by e.startDate, e.id
        """)
    List<CalendarEventFeedProjection> findVisible(
        @Param("states") Collection<StateEnum> states,
        @Param("from") Date from,
        @Param("to") Date to
    );
}
