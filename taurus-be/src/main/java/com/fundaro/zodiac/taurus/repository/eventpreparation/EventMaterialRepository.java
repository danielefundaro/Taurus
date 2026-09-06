package com.fundaro.zodiac.taurus.repository.eventpreparation;

import com.fundaro.zodiac.taurus.domain.eventpreparation.CalendarEventMaterial;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventMaterialRepository extends JpaRepository<CalendarEventMaterial, Long> {
    @Query("select m from CalendarEventMaterial m join fetch m.item left join fetch m.assignment where m.event.id = :eventId and m.deleted = false order by m.displayOrder")
    List<CalendarEventMaterial> findCurrent(@Param("eventId") Long eventId);
    @Query("select m from CalendarEventMaterial m join fetch m.item left join fetch m.assignment where m.id = :id and m.event.id = :eventId and m.deleted = false")
    Optional<CalendarEventMaterial> findCurrentById(@Param("eventId") Long eventId, @Param("id") Long id);
}
