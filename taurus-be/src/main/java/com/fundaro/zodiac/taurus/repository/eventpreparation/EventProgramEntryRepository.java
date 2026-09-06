package com.fundaro.zodiac.taurus.repository.eventpreparation;

import com.fundaro.zodiac.taurus.domain.eventpreparation.CalendarEventProgramEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventProgramEntryRepository extends JpaRepository<CalendarEventProgramEntry, Long> {
    @Query("select e from CalendarEventProgramEntry e join fetch e.track t where e.event.id = :eventId and e.deleted = false order by e.displayOrder")
    List<CalendarEventProgramEntry> findCurrent(@Param("eventId") Long eventId);
}
