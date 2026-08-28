package com.fundaro.zodiac.taurus.service.user.external.impl;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service Implementation of ROLE_USER_EXTERNAL for managing {@link CalendarEvents}.
 * Restricts visibility to PUBLIC state only.
 */
@Service("ExternalPermissionsCalendarEventsService")
@Transactional
public class CalendarEventsServiceImpl extends com.fundaro.zodiac.taurus.service.user.impl.CalendarEventsServiceImpl {

    public CalendarEventsServiceImpl(
        com.fundaro.zodiac.taurus.service.CalendarEventsService adminCalendarEventsService
    ) {
        super(adminCalendarEventsService);
    }

    @Override
    protected List<StateEnum> getVisibleStates() {
        return List.of(StateEnum.PUBLIC);
    }
}
