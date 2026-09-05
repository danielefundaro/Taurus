package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.repository.projection.CalendarAttentionProjection;
import com.fundaro.zodiac.taurus.repository.projection.CalendarResponseProjection;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventsRepository extends CatalogRepository<CalendarEvents> {
    List<CalendarEvents> findAllBySeries_IdOrderByOriginalStartDateAsc(Long seriesId);

    @Query("""
        select distinct e from CalendarEvents e
        join fetch e.availabilities availability
        join fetch availability.user user
        where e.deleted = false
          and e.startDate > :now
          and user.keycloakId = :userId
          and availability.availability = :availability
        """)
    List<CalendarEvents> findFutureAvailableForUser(
        @Param("userId") String userId,
        @Param("now") Date now,
        @Param("availability") com.fundaro.zodiac.taurus.domain.CalendarEventAvailability.Availability availability
    );

    @Query("""
        select count(e.id) as eventCount, min(e.startDate) as earliestStartDate
        from CalendarEvents e
        where e.deleted = false
          and (e.seriesExcluded = false or e.seriesExcluded is null)
          and e.state in :states
          and e.startDate >= :from
          and e.startDate <= :to
          and not exists (
            select availability.id from CalendarEvents candidate
            join candidate.availabilities availability
            where candidate.id = e.id
              and availability.user.keycloakId = :userId
          )
        """)
    CalendarAttentionProjection summarizeMissingAvailability(
        @Param("userId") String userId,
        @Param("states") Collection<StateEnum> states,
        @Param("from") Date from,
        @Param("to") Date to
    );

    @Query("""
        select e.id as eventId, e.name as eventName, e.startDate as startDate, e.state as state,
               count(distinct availability.user.keycloakId) as responseCount
        from CalendarEvents e
        left join e.availabilities availability on availability.user.keycloakId in :expectedUserIds
        where e.deleted = false
          and (e.seriesExcluded = false or e.seriesExcluded is null)
          and e.state = :state
          and e.startDate >= :from
          and e.startDate <= :to
        group by e.id, e.name, e.startDate, e.state
        order by e.startDate asc, e.id asc
        """)
    List<CalendarResponseProjection> summarizeResponses(
        @Param("state") StateEnum state,
        @Param("expectedUserIds") Collection<String> expectedUserIds,
        @Param("from") Date from,
        @Param("to") Date to
    );
}
