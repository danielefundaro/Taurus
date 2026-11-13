package com.fundaro.zodiac.taurus.repository.finance;

import com.fundaro.zodiac.taurus.domain.finance.EventPayoutAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventPayoutAdjustmentRepository extends JpaRepository<EventPayoutAdjustment, Long> {
    List<EventPayoutAdjustment> findAllByEventIdAndDeletedFalse(Long eventId);

    List<EventPayoutAdjustment> findAllByEventIdInAndDeletedFalse(Collection<Long> eventIds);

    Optional<EventPayoutAdjustment> findByEventIdAndUserIdAndDeletedFalse(Long eventId, Long userId);
}
