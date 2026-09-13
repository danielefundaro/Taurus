package com.fundaro.zodiac.taurus.repository.eventpreparation;

import com.fundaro.zodiac.taurus.domain.eventpreparation.CalendarEventPreparation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface EventPreparationRepository extends JpaRepository<CalendarEventPreparation, Long> {
    Optional<CalendarEventPreparation> findByEvent_IdAndDeletedFalse(Long eventId);

    @Query("""
        select preparation from CalendarEventPreparation preparation
        join fetch preparation.event event
        where preparation.deleted = false
          and event.deleted = false
          and (event.seriesExcluded = false or event.seriesExcluded is null)
          and event.startDate <= :to
          and event.endDate >= :from
        order by event.startDate asc, event.id asc
        """)
    List<CalendarEventPreparation> findDashboardCandidates(@Param("from") Date from, @Param("to") Date to);
}
