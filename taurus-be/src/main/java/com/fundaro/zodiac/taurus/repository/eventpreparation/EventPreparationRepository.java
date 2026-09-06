package com.fundaro.zodiac.taurus.repository.eventpreparation;

import com.fundaro.zodiac.taurus.domain.eventpreparation.CalendarEventPreparation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventPreparationRepository extends JpaRepository<CalendarEventPreparation, Long> {
    Optional<CalendarEventPreparation> findByEvent_IdAndDeletedFalse(Long eventId);
}
