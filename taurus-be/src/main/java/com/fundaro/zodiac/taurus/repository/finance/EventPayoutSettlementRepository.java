package com.fundaro.zodiac.taurus.repository.finance;

import com.fundaro.zodiac.taurus.domain.finance.EventPayoutSettlement;
import com.fundaro.zodiac.taurus.repository.projection.PayoutSettledEventProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

public interface EventPayoutSettlementRepository extends JpaRepository<EventPayoutSettlement, Long> {
    List<EventPayoutSettlement> findAllByEventIdAndDeletedFalse(Long eventId);

    List<EventPayoutSettlement> findAllByEventIdInAndDeletedFalse(Collection<Long> eventIds);

    List<EventPayoutSettlement> findAllByUserIdAndDeletedFalse(Long userId);

    List<EventPayoutSettlement> findAllByMovementIdAndDeletedFalse(Long movementId);

    List<EventPayoutSettlement> findAllByMovementIdInAndDeletedFalse(Collection<Long> movementIds);

    /** The movements, among the given ones, that pay at least one share: the expenses of an event leave them out. */
    @Query("""
        select distinct settlement.movementId from EventPayoutSettlement settlement
        where settlement.deleted = false
          and settlement.movementId in :movementIds
        """)
    Set<Long> findSettlingMovementIds(@Param("movementIds") Collection<Long> movementIds);

    /** The events already settled whose start falls in the period, the ones a new dated version would recalculate. */
    @Query("""
        select event.id as eventId, event.name as eventName, event.startDate as startDate,
               sum(settlement.amount) as settledAmount
        from EventPayoutSettlement settlement, CalendarEvents event
        where event.id = settlement.eventId
          and settlement.deleted = false
          and event.startDate >= :from
          and event.startDate < :to
        group by event.id, event.name, event.startDate
        order by event.startDate asc, event.id asc
        """)
    List<PayoutSettledEventProjection> findSettledEventsBetween(@Param("from") Date from, @Param("to") Date to);
}
