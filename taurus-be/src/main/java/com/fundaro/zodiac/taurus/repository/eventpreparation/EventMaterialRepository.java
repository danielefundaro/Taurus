package com.fundaro.zodiac.taurus.repository.eventpreparation;

import com.fundaro.zodiac.taurus.domain.eventpreparation.CalendarEventMaterial;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueSeverity;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventMaterialRepository extends JpaRepository<CalendarEventMaterial, Long> {
    @Query("select m from CalendarEventMaterial m join fetch m.item left join fetch m.assignment where m.event.id = :eventId and m.deleted = false order by m.displayOrder")
    List<CalendarEventMaterial> findCurrent(@Param("eventId") Long eventId);

    @Query("select m from CalendarEventMaterial m join fetch m.item left join fetch m.assignment where m.id = :id and m.event.id = :eventId and m.deleted = false")
    Optional<CalendarEventMaterial> findCurrentById(@Param("eventId") Long eventId, @Param("id") Long id);

    /** The rows of the events, each with the number of open issues of the given severity on its item. */
    @Query("""
        select m, (select count(issue) from InventoryIssueReport issue
                   where issue.item = m.item and issue.deleted = false and issue.severity = :severity and issue.status in :statuses)
        from CalendarEventMaterial m
        join fetch m.item
        left join fetch m.assignment
        where m.event.id in :eventIds and m.deleted = false
        order by m.event.id, m.displayOrder
        """)
    List<Object[]> findCurrentByEventIds(@Param("eventIds") Collection<Long> eventIds, @Param("severity") InventoryIssueSeverity severity,
                                         @Param("statuses") Collection<InventoryIssueStatus> statuses);
}
